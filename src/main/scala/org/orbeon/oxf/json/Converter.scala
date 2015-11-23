/**
 * Copyright (c) 2015 Orbeon, Inc. http://orbeon.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.orbeon.oxf.json

import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml._
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import spray.json._

//
// Functions to convert JSON to XML and back following the XForms 2.0 specification.
//
// The conversion follows the following principles:
//
// - Any JSON document is convertible to XML.
// - However, the opposite is not true, and only XML documents following a very specific pattern
//   can be converted to JSON. In other words the purpose of the conversion rules is to expose JSON
//   to XML processing and not the other way around.
// - XPath expressions which apply to the resulting XML document feel as natural as possible in most
//   cases and can be written just by looking at the original JSON.
//
// Remaining tasks:
//
// - handle escaped characters for JSON strings ("Escaped characters are transformed as necessary")
// - `strict = true`: validate more cases
// - `strict = false`: make sure fallbacks are in place and that any XML can be thrown in without error
//
object Converter {

  // Convert a JSON String to a readonly DocumentInfo
  def jsonStringToXML(source: String): DocumentInfo = {
    val (builder, receiver) = TransformerUtils.createTinyBuilder(XPath.GlobalConfiguration)
    jsonStringToXML(source, receiver)
    builder.getCurrentRoot.asInstanceOf[DocumentInfo]
  }

  // Convert a JSON String to a stream of XML events
  def jsonStringToXML(source: String, receiver: XMLReceiver): Unit =
    jsonToXML(source.parseJson, receiver)

  // Convert a JSON AST to a stream of XML events
  def jsonToXML(ast: JsValue, receiver: XMLReceiver): Unit = {

    import XMLReceiverSupport._

    implicit val rcv = new DeferredXMLReceiverImpl(receiver)

    def processValue(jsValue: JsValue): Unit =
      jsValue match {
        case JsString(v) ⇒
          rcv.addAttribute(Symbols.Type, Symbols.String)
          text(v)
        case JsNumber(v) ⇒
          rcv.addAttribute(Symbols.Type, Symbols.Number)
          text(v.toString)
        case JsBoolean(v) ⇒
          rcv.addAttribute(Symbols.Type, Symbols.Boolean)
          text(v.toString)
        case JsNull ⇒
          rcv.addAttribute(Symbols.Type, Symbols.Null)
        case JsObject(fields) ⇒
          rcv.addAttribute(Symbols.Object, Symbols.True)
          fields foreach { case (name, value) ⇒

            val ncName  = SaxonUtils.makeNCName(name, keepFirstIfPossible = true)
            val nameAtt = ncName != name list (Symbols.Name → name)

            def attsForArray = (Symbols.Array → Symbols.True) :: nameAtt

            value match {
              case JsArray(arrayValues) if arrayValues.isEmpty ⇒
                element(ncName, attsForArray)
              case JsArray(arrayValues) ⇒
                val atts = attsForArray
                arrayValues foreach { arrayValue ⇒
                  withElement(ncName, atts) {
                    processValue(arrayValue)
                  }
                }
              case _ ⇒
                withElement(ncName, nameAtt) {
                  processValue(value)
                }
            }
          }
        case JsArray(arrayValues) if arrayValues.isEmpty ⇒
          element(Symbols.Anonymous, List(Symbols.Name → "", Symbols.Array → Symbols.True))
        case JsArray(arrayValues) ⇒
          arrayValues foreach { arrayValue ⇒
            withElement(Symbols.Anonymous, List(Symbols.Name → "", Symbols.Array → Symbols.True)) {
              processValue(arrayValue)
            }
          }
      }

    withDocument {
      withElement(Symbols.JSON) {
        processValue(ast)
      }
    }
  }

  // Convert an XML tree to a JSON String
  def xmlToJsonString(root: NodeInfo, strict: Boolean): String =
    xmlToJson(root, strict).toString

  // Convert an XML tree to a JSON AST
  def xmlToJson(root: NodeInfo, strict: Boolean): JsValue = {

    import org.orbeon.scaxon.XML._

    def isArray (elem: NodeInfo) = (elem attValue    Symbols.Array)  == Symbols.True
    def isObject(elem: NodeInfo) = (elem attValue    Symbols.Object) == Symbols.True
    def typeOpt (elem: NodeInfo) =  elem attValueOpt Symbols.Type
    def elemName(elem: NodeInfo) =  elem attValueOpt Symbols.Name getOrElse elem.localname

    def throwError(s: String) =
      throw new IllegalArgumentException(s)

    def isEmptyArray(elem: NodeInfo) =
      typeOpt(elem).isEmpty && ! isObject(elem) && ! hasChildElement(elem)

    def processElement(elem: NodeInfo): JsValue =
      (typeOpt(elem), isObject(elem)) match {
        case (Some(Symbols.String) , _) ⇒ JsString(elem.stringValue)
        case (Some(Symbols.Number) , _) ⇒ JsNumber(elem.stringValue)
        case (Some(Symbols.Boolean), _) ⇒ JsBoolean(elem.stringValue.toBoolean)
        case (Some(Symbols.Null)   , _) ⇒ JsNull
        case (Some(other)          , _) ⇒ // invalid `type` attribute
          if (strict)
            throwError(s"""unknown datatype `type="$other"`""")
          JsNull
        case (None, true) ⇒ // `object="true"`

          val childrenGroupedByName = elem / * groupByKeepOrder elemName

          val fields =
            childrenGroupedByName map { case (name, elems @ head +: tail) ⇒
              name → {
                if (isArray(head)) {
                  if (strict && ! (elems forall isArray))
                    throwError(s"""all array elements with name $name must have `array="true"`""")

                  if (isEmptyArray(head))
                    JsArray()
                  else
                    JsArray(elems map processElement toVector)
                } else {
                  if (strict && tail.nonEmpty)
                    throwError(s"""only one element with name $name is allowed when there is no `array="true"`""")
                  processElement(head)
                }
              }
            }

          JsObject(fields.toMap)

        case (None, false) ⇒ // must be an anonymous array

          val childrenGroupedByName = elem / * groupByKeepOrder elemName

          childrenGroupedByName.headOption match {
            case Some((name, elems @ head +: tail)) ⇒

              if (strict && name != "")
                throwError("""anonymous array elements must have `name=""`""")
              if (strict && ! (elems forall isArray))
                throwError("""all anonymous array elements must have `array="true"`""")

              if (isEmptyArray(head))
                JsArray()
              else
                JsArray(elems map processElement toVector)

            case None ⇒
              if (strict)
                throwError("""anonymous array is missing child element with `array="true"`""")
              JsNull
          }
      }

    processElement(
      if (isDocument(root))
        root.rootElement
      else if (isElement(root))
        root
      else
        throwError("node must be an element or document")
    )
  }

  private object Symbols {
    val Object    = "object"
    val Array     = "array"
    val String    = "string"
    val Number    = "number"
    val Boolean   = "boolean"
    val Null      = "null"

    val True      = "true"
    val Type      = "type"
    val Name      = "name"
    val JSON      = "json"
    val Anonymous = "_"
  }
}