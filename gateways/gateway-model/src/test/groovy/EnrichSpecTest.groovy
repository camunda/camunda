package io.camunda.gateway.protocol.model

import org.junit.jupiter.api.Test
import static org.assertj.core.api.Assertions.assertThat

/**
 * Unit tests for the EnrichSpec preprocessor at src/build/groovy/EnrichSpec.groovy.
 *
 * The script is invoked via GroovyShell with two args (input and output spec paths). Tests
 * construct minimal in-memory specs, write them to temp files, run the script, and inspect the
 * resulting enriched YAML.
 */
class EnrichSpecTest {

  // Path to the script under test, relative to the project base dir (working dir for surefire).
  private static final File SCRIPT = new File('src/build/groovy/EnrichSpec.groovy')

  @Test
  void typeMappingsParity_offsetDateTimeMappingMirroredFromPom() {
    // Required-field types whose Java representation differs from the schema name MUST be in
    // the preprocessor's TYPE_OVERRIDES table. The pom's <typeMappings> source-of-truth must be
    // mirrored. Today only `OffsetDateTime → String` matters for required-field types; other
    // pom mappings target zero-required filter properties that don't appear in any chain.
    def pomText = new File('pom.xml').text
    def offsetDateTimeMapping = pomText.contains('<typeMapping>OffsetDateTime=String</typeMapping>')
    assertThat(offsetDateTimeMapping)
        .as('pom must declare OffsetDateTime → String typeMapping')
        .isTrue()

    def scriptText = SCRIPT.text
    assertThat(scriptText)
        .as("EnrichSpec's TYPE_OVERRIDES must mirror the pom's OffsetDateTime mapping")
        .contains("'OffsetDateTime': 'String'")
  }

  @Test
  void mergedChain_walksParentRequiredBeforeOwn() {
    // Two-schema spec: Child extends Parent via allOf $ref. Required field order must be
    // parent-required-first, then child-required.
    def specYaml = '''\
openapi: 3.0.0
info: { title: t, version: '1' }
paths: {}
components:
  schemas:
    Parent:
      type: object
      required: [a, b]
      properties:
        a: { type: string }
        b: { type: integer, format: int64 }
    Child:
      type: object
      allOf: [{ $ref: '#/components/schemas/Parent' }]
      required: [c]
      properties:
        c: { type: boolean }
'''
    def enriched = runScript(specYaml)
    def chain = enriched.components.schemas.Child['x-staged-chain']
    assertThat(chain*.name).containsExactly('a', 'b', 'c')
    assertThat(chain[0].nextStage).isEqualTo('B')
    assertThat(chain[1].nextStage).isEqualTo('C')
    assertThat(chain[2].isLast).isTrue()
    assertThat(chain[2].nextStage).isNull()
    assertThat(chain*.type).containsExactly('String', 'Long', 'Boolean')
  }

  @Test
  void zeroRequired_emitsNoChain() {
    def specYaml = '''\
openapi: 3.0.0
info: { title: t, version: '1' }
paths: {}
components:
  schemas:
    Filter:
      type: object
      properties:
        a: { type: string }
'''
    def enriched = runScript(specYaml)
    assertThat(enriched.components.schemas.Filter['x-staged-chain']).isNull()
  }

  /**
   * Helper: write the given spec YAML to a temp dir, invoke the EnrichSpec script with input
   * and output paths in that dir, and load+return the enriched output as a Map.
   */
  private Map runScript(String specYaml) {
    def tmpDir = java.nio.file.Files.createTempDirectory('enrich-spec-test').toFile()
    def inputFile = new File(tmpDir, 'rest-api.yaml')
    inputFile.text = specYaml
    def outputDir = new File(tmpDir, 'out')
    outputDir.mkdirs()
    def outputFile = new File(outputDir, 'rest-api.yaml')

    new GroovyShell().run(SCRIPT, [inputFile.path, outputFile.path] as String[])

    return new org.yaml.snakeyaml.Yaml().load(outputFile.text) as Map
  }
}
