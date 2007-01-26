    /**
 *  Copyright (C) 2006 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.List;

/**
 * Represents an xforms:output control.
 */
public class XFormsOutputControl extends XFormsValueControl {

    // Optional display format
    private String format;

    // Value attribute
    private String valueAttribute;

    // XForms 1.1 mediatype attribute
    private String mediatypeAttribute;

    private boolean urlNorewrite;

    public XFormsOutputControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
        this.format = element.attributeValue(new QName("format", XFormsConstants.XXFORMS_NAMESPACE));
        this.mediatypeAttribute = element.attributeValue("mediatype");
        this.valueAttribute = element.attributeValue("value");
        this.urlNorewrite = XFormsUtils.resolveUrlNorewrite(element);
    }

    protected void evaluateValue(final PipelineContext pipelineContext) {
        final String rawValue;
        if (valueAttribute == null) {
            // Get value from single-node binding
            final NodeInfo currentSingleNode = bindingContext.getSingleNode();
            if (currentSingleNode != null)
                rawValue = XFormsInstance.getValueForNodeInfo(currentSingleNode);
            else
                rawValue = "";
        } else {
            // Value comes from the XPath expression within the value attribute
            final List currentNodeset = bindingContext.getNodeset();
            if (currentNodeset != null && currentNodeset.size() > 0) {
                rawValue = containingDocument.getEvaluator().evaluateAsString(pipelineContext,
                        currentNodeset, bindingContext.getPosition(),
                        valueAttribute, Dom4jUtils.getNamespaceContextNoDefault(getControlElement()), null, containingDocument.getXFormsControls().getFunctionLibrary(), null);
            } else {
                rawValue = "";
            }
        }

        // Handle image mediatype if necessary
        final String updatedValue;
        if (mediatypeAttribute != null && mediatypeAttribute.startsWith("image/")) {
            final String type = getType();
            if (!urlNorewrite && (type == null || type.equals(XMLUtils.buildExplodedQName(XMLConstants.XSD_URI, "anyURI")))) {
                // Rewrite image URI
                updatedValue = XFormsUtils.resolveURL(containingDocument, pipelineContext, getControlElement(), false, rawValue);
            } else {
                updatedValue = rawValue;
            }
        } else if ("text/html".equals(mediatypeAttribute)) {
            // In case of HTML, we may need to do some URL rewriting
            // Check src and href attributes for now. Anything else?
            final boolean needsRewrite = !urlNorewrite && (rawValue.indexOf("src=") != -1 || rawValue.indexOf("href=") != -1);
            if (needsRewrite) {
                // Rewrite URLs
                final StringBuffer sb = new StringBuffer();
                // NOTE: we do our own serialization here, but it's really simple (no namespaces) and probably reasonably efficient
                XFormsUtils.streamHTMLFragment(new ForwardingContentHandler() {

                    public void characters(char[] chars, int start, int length) throws SAXException {
                        sb.append(XMLUtils.escapeXMLMinimal(new String(chars, start, length)));// NOTE: not efficient to create a new String here
                    }

                    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                        sb.append('<');
                        sb.append(localname);
                        final int attributeCount = attributes.getLength();
                        for (int i = 0; i < attributeCount; i++) {

                            final String currentName = attributes.getLocalName(i);
                            final String currentValue = attributes.getValue(i);

                            final String rewrittenValue;
                            if ("src".equals(currentName) || "href".equals(currentName)) {
                                rewrittenValue = XFormsUtils.resolveURL(containingDocument, pipelineContext, getControlElement(), false, currentValue);
                            } else {
                                rewrittenValue = currentValue;
                            }

                            sb.append(' ');
                            sb.append(currentName);
                            sb.append("=\"");
                            sb.append(XMLUtils.escapeXMLMinimal(rewrittenValue));
                            sb.append('"');
                        }
                        sb.append('>');
                    }

                    public void endElement(String uri, String localname, String qName) throws SAXException {
                        sb.append("</");
                        sb.append(localname);
                        sb.append('>');
                    }
                }, rawValue, getLocationData(), "xhtml");
                updatedValue = sb.toString();
            } else {
                updatedValue = rawValue;
            }

        } else {
            updatedValue = rawValue;
        }

        super.setValue(updatedValue);
    }

    public void evaluateDisplayValue(PipelineContext pipelineContext) {
        if (valueAttribute == null) {
            evaluateDisplayValueUseFormat(pipelineContext, format);
        } else {
            setDisplayValue(null);
        }
    }

    public String getMediatypeAttribute() {
        return mediatypeAttribute;
    }

    public String getValueAttribute() {
        return valueAttribute;
    }
}
