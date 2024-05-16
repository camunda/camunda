package io.camunda.tasklist.es;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.manager.ElasticsearchSchemaManager;
import io.camunda.tasklist.schema.manager.SchemaManager;
import io.camunda.tasklist.util.NoSqlHelper;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class IndexSchemaValidatorIT extends TasklistIntegrationTest {

  private static final String ORIGINAL_SCHEMA_PATH = "/tasklist-test.json";
  private static final String INDEX_NAME = "test";

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private RetryElasticsearchClient retryElasticsearchClient;
  @Autowired private IndexSchemaValidator indexSchemaValidator;
  @Autowired private NoSqlHelper noSqlHelper;
  @Autowired private SchemaManager schemaManager;
  @Autowired private ElasticsearchSchemaManager elasticsearchSchemaManager;

  private String originalSchemaContent;

  private IndexDescriptor indexDescriptor;

  @BeforeEach
  public void setUp() throws Exception {

     indexDescriptor = new IndexDescriptor() {
      @Override
      public String getIndexName() {
        return INDEX_NAME;
      }

      @Override
      public String getFullQualifiedName() {
        return idxName(INDEX_NAME);
      }

      @Override
      public String getSchemaClasspathFilename() {
        return ORIGINAL_SCHEMA_PATH;
      }

      @Override
      public String getAllVersionsIndexNameRegexPattern() {
        return idxName(INDEX_NAME)+"*";
      }
    };

    // Read the original schema content
    originalSchemaContent = new String(Files.readAllBytes(Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH).toURI())));
    assertThat(originalSchemaContent).doesNotContain("\"prop2\"");
  }

  @AfterEach
  public void tearDown() throws Exception {
    // Restore the original schema content
    Files.write(Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH).toURI()), originalSchemaContent.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
    retryElasticsearchClient.deleteIndicesFor(schemaManager.getIndexPrefix() +"-"+ INDEX_NAME);
  }

  @Test
  public void shouldValidateDynamicIndexWithAddedProperty() throws Exception {
    // Create a new list containing only the new IndexDescriptor
    final Set<IndexDescriptor> newIndexDescriptorsSet = new HashSet<>(Collections.singleton(indexDescriptor));

    // Use reflection to replace the indexDescriptors field in IndexSchemaValidator
    replaceIndexDescriptors(indexSchemaValidator, newIndexDescriptorsSet);

    // Create a new Schema on ElasticSearch
    schemaManager.createIndex(indexDescriptor);
    // Validate the schema before updating
    var diff = indexSchemaValidator.validateIndexMappings();
    assertThat(diff).isEmpty(); // No differences expected yet

    final Map<String, Object> document = Map.of("prop0", "test");
    final boolean created2 = retryElasticsearchClient.createOrUpdateDocument(indexDescriptor.getFullQualifiedName(), "id", document);
    System.out.println("Created: "+created2);

    // Update the schema file to include the new property
    final String updatedSchemaContent = originalSchemaContent.replace(
        "\"properties\": {",
        "\"properties\": {\n    \"prop2\": { \"type\": \"keyword\" },"
    );
    Files.write(Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH).toURI()), updatedSchemaContent.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);

    // Ensure the schema file was updated correctly
    final String newSchemaContent = new String(Files.readAllBytes(Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH).toURI())));
    assertThat(newSchemaContent).contains("\"prop2\"");

    // Validate the schema after updating
    diff = indexSchemaValidator.validateIndexMappings();
    assertThat(diff).isNotEmpty(); // Expecting a difference due to the new property
  }

  @Test
  public void shouldValidateDynamicIndexWithRemovedPropertyAndWillIgnoreRemovals() throws Exception {
    // Create a new list containing only the new IndexDescriptor
    final Set<IndexDescriptor> newIndexDescriptorsSet = new HashSet<>(Collections.singleton(indexDescriptor));

    // Use reflection to replace the indexDescriptors field in IndexSchemaValidator
    replaceIndexDescriptors(indexSchemaValidator, newIndexDescriptorsSet);

    // Create a new Schema on ElasticSearch
    schemaManager.createIndex(indexDescriptor);
    // Validate the schema before updating
    var diff = indexSchemaValidator.validateIndexMappings();
    assertThat(diff).isEmpty(); // No differences expected yet

    final Map<String, Object> document = Map.of("prop0", "test");
    final boolean created2 = retryElasticsearchClient.createOrUpdateDocument(indexDescriptor.getFullQualifiedName(), "id", document);
    System.out.println("Created: "+created2);

    // Update the schema file to include the new property
    final String updatedSchemaContent = originalSchemaContent.replace(
        "    \"properties\": {\n"
            + "      \"prop0\": {\n"
            + "        \"type\": \"keyword\"\n"
            + "      },",
        "\"properties\": {"
    );
    Files.write(Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH).toURI()), updatedSchemaContent.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
    // Ensure the schema file was updated correctly
    final String newSchemaContent = new String(Files.readAllBytes(Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH).toURI())));
    assertThat(newSchemaContent).doesNotContain("\"prop0\"");

    // Validate the schema after updating
    diff = indexSchemaValidator.validateIndexMappings();
    assertThat(diff).isEmpty(); // It will ignore the removal of the property - Not allowed 
  }

  private String idxName(final String name) {
    return schemaManager.getIndexPrefix() + "-" + name;
  }

  private void replaceIndexDescriptors(final Object target, final Set<IndexDescriptor> newIndexDescriptorsSet) throws NoSuchFieldException, IllegalAccessException {
    // Use reflection to access and replace the indexDescriptors field
    final Field indexDescriptorsField = target.getClass().getDeclaredField("indexDescriptors");
    indexDescriptorsField.setAccessible(true);
    indexDescriptorsField.set(target, newIndexDescriptorsSet);
  }
}
