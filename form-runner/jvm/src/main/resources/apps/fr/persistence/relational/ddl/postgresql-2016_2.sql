CREATE TABLE orbeon_form_definition (
    created             TIMESTAMP,
    last_modified_time  TIMESTAMP,
    last_modified_by    VARCHAR(255),
    app                 VARCHAR(255),
    form                VARCHAR(255),
    form_version        INT NOT NULL,
    form_metadata       VARCHAR(4000),
    deleted             CHARACTER(1)  NOT NULL,
    xml                 XML
);

CREATE TABLE orbeon_form_definition_attach (
    created             TIMESTAMP,
    last_modified_time  TIMESTAMP,
    last_modified_by    VARCHAR(255),
    app                 VARCHAR(255),
    form                VARCHAR(255),
    form_version        INT NOT NULL,
    deleted             CHARACTER(1) NOT NULL,
    file_name           VARCHAR(255),
    file_content        BYTEA
);

CREATE TABLE orbeon_form_data (
    id SERIAL           PRIMARY KEY NOT NULL,
    created             TIMESTAMP,
    last_modified_time  TIMESTAMP,
    last_modified_by    VARCHAR(255),
    username            VARCHAR(255),
    groupname           VARCHAR(255),
    app                 VARCHAR(255),
    form                VARCHAR(255),
    form_version        INT NOT NULL,
    document_id         VARCHAR(255),
    deleted             CHARACTER(1) NOT NULL,
    draft               CHARACTER(1) NOT NULL,
    xml                 XML
);

CREATE TABLE orbeon_form_data_attach (
    created             TIMESTAMP,
    last_modified_time  TIMESTAMP,
    last_modified_by    VARCHAR(255),
    username            VARCHAR(255),
    groupname           VARCHAR(255),
    app                 VARCHAR(255),
    form                VARCHAR(255),
    form_version        INT NOT NULL,
    document_id         VARCHAR(255),
    deleted             CHARACTER(1) NOT NULL,
    draft               CHARACTER(1) NOT NULL,
    file_name           VARCHAR(255),
    file_content        BYTEA
);

-- New index tables

CREATE TABLE orbeon_i_current (
    data_id             INT          NOT NULL,
    created             TIMESTAMP    NOT NULL,
    last_modified_time  TIMESTAMP    NOT NULL,
    last_modified_by    VARCHAR(255)         ,
    username            VARCHAR(255)         ,
    groupname           VARCHAR(255)         ,
    app                 VARCHAR(255) NOT NULL,
    form                VARCHAR(255) NOT NULL,
    form_version        INT          NOT NULL,
    document_id         VARCHAR(255) NOT NULL,
    draft               CHARACTER(1) NOT NULL,
    FOREIGN KEY         (data_id)    REFERENCES orbeon_form_data(id)
);

CREATE TABLE orbeon_i_control_text (
    data_id             INT          NOT NULL,
    pos                 INT          NOT NULL,
    control             VARCHAR(255) NOT NULL,
    val                 TEXT         NOT NULL,
    FOREIGN KEY         (data_id)    REFERENCES orbeon_form_data(id)
);
