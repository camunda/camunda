# Camunda Process Test Starter LangChain4j

LangChain4j bridge for Camunda Process Test
[LLM-as-a-judge assertions](../camunda-process-test-java/README.md) and
[semantic similarity assertions](../camunda-process-test-java/README.md).

Provides LangChain4j-backed implementations for both features:

- **Judge**: a `ChatModelAdapter` backed by LangChain4j chat models (OpenAI, Anthropic, Amazon
  Bedrock, OpenAI-compatible)
- **Semantic similarity**: an `EmbeddingModelAdapter` backed by LangChain4j embedding models
  (OpenAI, Azure OpenAI, Amazon Bedrock, OpenAI-compatible)

## Install

Add the following dependency to your Maven project alongside `camunda-process-test-java`:

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>camunda-process-test-langchain4j</artifactId>
  <scope>test</scope>
</dependency>
```

If the project is relying on `camunda-process-test-spring`, no additional dependency is required.

---

## Judge (LLM-as-a-judge)

Judge configuration is read from `camunda-container-runtime.properties` file if `camunda-process-test-java` is being used.
If used in combination with `camunda-process-test-spring` application properties are to be preferred.

Set `judge.chatModel.provider` to one of the supported provider names.

### OpenAI

```properties
judge.chatModel.provider=openai
judge.chatModel.apiKey=<your api key>
judge.chatModel.model=gpt-5-nano
```

### Anthropic

```properties
judge.chatModel.provider=anthropic
judge.chatModel.apiKey=<your api key>
judge.chatModel.model=claude-haiku-4-5-20251001
```

### Amazon Bedrock

```properties
judge.chatModel.provider=amazon-bedrock
judge.chatModel.region=eu-central-1
judge.chatModel.model=eu.anthropic.claude-sonnet-4-5-20250929-v1:0
judge.chatModel.credentials.accessKey=<your access key>                     # optional; uses default credentials chain if absent
judge.chatModel.credentials.secretKey=<your secret key>                     # optional
judge.chatModel.apiKey=<your api key>                                       # optional, instead of accessKey and secretKey
```

### OpenAI-compatible

```properties
judge.chatModel.provider=openai-compatible
judge.chatModel.baseUrl=https://localhost:12434/v1
judge.chatModel.apiKey=<your api key, if needed>                             # optional
judge.chatModel.model=llama3
```

### Programmatic usage

```java
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.judge.JudgeConfig;

ChatModel model = OpenAiChatModel.builder()
  .apiKey("<your api key>")
  .modelName("gpt-5-nano")
  .build();

CamundaAssert.setJudgeConfig(JudgeConfig.of(model::chat));
```

### Spring bean registration

To use a custom `ChatModelAdapter` bean instead of the SPI, register it with the bean name
`"judge.<provider>"`. The `judge.` prefix avoids name collisions with beans registered for other
features (e.g. semantic similarity):

```java
@Bean("judge.openai")
ChatModelAdapter myChatModelAdapter() {
  // custom OpenAI chat model adapter ...
}
```

Then configure:

```properties
judge.chatModel.provider=openai
```

If only one `ChatModelAdapter` bean is present and no provider is configured, it is
selected automatically (no naming convention required).

### Usage in tests

```java
@Test
void shouldProcessOrder() {
  // ... start process, complete tasks ...

  assertThat(processInstance)
    .hasVariableSatisfiesJudge("orderSummary",
      "The order summary should mention the customer name and total price");
}
```

---

## Semantic similarity

Configuration is read from `camunda-container-runtime.properties` when using
`camunda-process-test-java`, or from Spring application properties when using
`camunda-process-test-spring`.

Set `similarity.embeddingModel.provider` to one of the supported provider names.

### OpenAI

```properties
similarity.embeddingModel.provider=openai
similarity.embeddingModel.apiKey=<your api key>
similarity.embeddingModel.model=text-embedding-3-small
similarity.embeddingModel.dimensions=512                                     # optional
similarity.embeddingModel.headers.<header-name>=<header-value>               # optional, can have multiple custom headers
```

### Azure OpenAI

```properties
similarity.embeddingModel.provider=azure-openai
similarity.embeddingModel.endpoint=https://my-resource.openai.azure.com/
similarity.embeddingModel.apiKey=<your api key>
similarity.embeddingModel.model=text-embedding-3-large
similarity.embeddingModel.dimensions=1024                                    # optional
similarity.embeddingModel.headers.<header-name>=<header-value>               # optional, can have multiple custom headers
```

### Amazon Bedrock

```properties
similarity.embeddingModel.provider=amazon-bedrock
similarity.embeddingModel.region=eu-central-1                               # optional, defaults to us-east-1
similarity.embeddingModel.model=amazon.titan-embed-text-v2:0
similarity.embeddingModel.credentials.accessKey=<your access key>           # optional; uses default credentials chain if absent
similarity.embeddingModel.credentials.secretKey=<your secret key>           # optional
similarity.embeddingModel.credentials.normalize=true                        # optional, defaults to no normalization
```

### OpenAI-compatible

```properties
similarity.embeddingModel.provider=openai-compatible
similarity.embeddingModel.baseUrl=http://localhost:11434/v1
similarity.embeddingModel.model=nomic-embed-text
similarity.embeddingModel.apiKey=<your api key, if needed>                   # optional
similarity.embeddingModel.headers.<header-name>=<header-value>               # optional, can have multiple custom headers
```

### Spring application properties

When using `camunda-process-test-spring`, use the `camunda.process-test.similarity.*` prefix:

```yaml
camunda:
  process-test:
    similarity:
      threshold: 0.85
      embeddingModel:
        provider: openai
        apiKey: ${OPENAI_API_KEY}
        model: text-embedding-3-small
```

Alternatively, register an `EmbeddingModelAdapter` bean with the name `"similarity.<provider>"`.
When the provider is configured, the resolver looks for a bean named
`"similarity.<provider>"`:

```java
@Bean("similarity.openai")
EmbeddingModelAdapter myEmbeddingModelAdapter() {
  EmbeddingModel model = OpenAiEmbeddingModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("text-embedding-3-small")
    .build();
  return text -> model.embed(text).content().vector();
}
```

Then configure:

```properties
similarity.embeddingModel.provider=openai
```

If only one `EmbeddingModelAdapter` bean is present and no provider is configured, it is
selected automatically (no naming convention required).

### Programmatic usage

```java
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.similarity.SemanticSimilarityConfig;

EmbeddingModel model = OpenAiEmbeddingModel.builder()
  .apiKey("<your api key>")
  .modelName("text-embedding-3-small")
  .build();

CamundaAssert.setSemanticSimilarityConfig(
  SemanticSimilarityConfig.of(text -> model.embed(text).content().vector())
    .withThreshold(0.85));
```

### Usage in tests

```java
@Test
void shouldProcessOrder() {
  // ... start process, complete tasks ...

  // Assert that a process variable is semantically similar to the expected value
  assertThat(processInstance)
    .hasVariableSimilarTo("orderSummary", "The order was processed successfully");

  // With a custom threshold
  assertThat(processInstance)
    .withSemanticSimilarityConfig(c -> c.withThreshold(0.9))
    .hasVariableSimilarTo("orderSummary", "The order was processed successfully");

  // Assert a local variable on a specific element
  assertThat(processInstance)
    .hasLocalVariableSimilarTo(ElementSelectors.byId("review-task"),
      "reviewComment", "The product quality is acceptable");
}
```

