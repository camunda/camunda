-- create_process_deployment_table
CREATE TABLE PROCESS_DEFINITION
(
    PROCESS_DEFINITION_KEY bigint NOT NULL,
    PROCESS_DEFINITION_ID  varchar(4000),
    NAME                   varchar(4000),
    RESOURCE_NAME          varchar(4000),
    BPMN_XML               varchar(MAX),
    TENANT_ID              varchar(4000),
    VERSION                smallint,
    VERSION_TAG            varchar(4000),
    FORM_ID                varchar(4000),
    CONSTRAINT PK_PROCESS_DEFINITION PRIMARY KEY (PROCESS_DEFINITION_KEY)
);

CREATE NONCLUSTERED INDEX IDX_PROCESS_DEFINITION_ID ON PROCESS_DEFINITION(PROCESS_DEFINITION_ID);

-- create_process_instance_table
CREATE TABLE PROCESS_INSTANCE
(
    PROCESS_INSTANCE_KEY        bigint NOT NULL,
    PROCESS_DEFINITION_ID       varchar(4000),
    PROCESS_DEFINITION_KEY      bigint,
    STATE                       varchar(20),
    START_DATE                  datetime2(3),
    END_DATE                    datetime2(3),
    TENANT_ID                   varchar(4000),
    PARENT_PROCESS_INSTANCE_KEY bigint,
    PARENT_ELEMENT_INSTANCE_KEY bigint,
    ELEMENT_ID                  varchar(4000),
    VERSION                     smallint,
    DESCRIPTION                 varchar(4000),
    TREE_PATH                   varchar(4000),
    ERROR_MESSAGE               varchar(4000),
    CONSTRAINT PK_PROCESS_INSTANCE PRIMARY KEY (PROCESS_INSTANCE_KEY)
);

