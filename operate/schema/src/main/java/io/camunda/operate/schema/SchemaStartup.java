/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.schema;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.migration.Migrator;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaStartup")
@DependsOn("databaseInfo")
@Profile("!test")
public class SchemaStartup {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaStartup.class);

  @Autowired private SchemaManager schemaManager;

  @Autowired private IndexSchemaValidator schemaValidator;

  @Autowired private Migrator migrator;

  @Autowired private OperateProperties operateProperties;

  @Autowired private MigrationProperties migrationProperties;

  @PostConstruct
  public void initializeSchema() throws MigrationException {
    try {
      LOGGER.info("SchemaStartup started.");
      LOGGER.info("SchemaStartup: validate index versions.");
      schemaValidator.validateIndexVersions();
      LOGGER.info("SchemaStartup: validate index mappings.");
      final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields =
          schemaValidator.validateIndexMappings();
      final boolean createSchema =
          DatabaseInfo.isOpensearch()
              ? operateProperties.getOpensearch().isCreateSchema()
              : operateProperties.getElasticsearch().isCreateSchema();
      if (createSchema && !schemaValidator.schemaExists()) {
        LOGGER.info("SchemaStartup: schema is empty or not complete. Indices will be created.");
        schemaManager.createSchema();
        LOGGER.info("SchemaStartup: update index mappings.");
        schemaManager.checkAndUpdateIndices();
      } else {
        LOGGER.info(
            "SchemaStartup: schema won't be created, it either already exist, or schema creation is disabled in configuration.");
      }

      if (!newFields.isEmpty()) {
        if (createSchema) {
          schemaManager.updateSchema(newFields);
        } else {
          LOGGER.info(
              "SchemaStartup: schema won't be updated as schema creation is disabled in configuration.");
        }
      }
      if (migrationProperties.isMigrationEnabled()) {
        LOGGER.info("SchemaStartup: migrate schema.");
        migrator.migrateData();
      }
      LOGGER.info("SchemaStartup finished.");
    } catch (final Exception ex) {
      LOGGER.error("Schema startup failed: " + ex.getMessage(), ex);
      throw ex;
    }
  }
}
