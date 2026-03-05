# Camunda Process Test Starter LangChain4j

LangChain4j bridge for Camunda Process
Test [LLM-as-a-judge assertions](../camunda-process-test-java/README.md).

Provides a `JudgeConfigBootstrapProvider` implementation that creates a `JudgeConfig` containing a `ChatModelAdapter` implementation from
LangChain4j chat models, supporting OpenAI, Anthropic, Amazon Bedrock, and any
OpenAI-compatible endpoint.

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

## Configuration

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
judge.chatModel.credentials.accessKey=<your access key                      # optional; uses default credentials chain if absent
judge.chatModel.credentials.secretKey=<your secret key>                     # optional
judge.chatModel.credentials.apiKey=<your api key>                           # optional, instead of accessKey and secretKey
```

### OpenAI-compatible

```properties
judge.chatModel.provider=openai-compatible
judge.chatModel.baseUrl=https://localhost:12434/v1
judge.chatModel.apiKey=<your api key, if needed>                            # optional
judge.chatModel.model=llama3
```

## Programmatic usage

You can also configure the judge directly in code without a properties file:

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

## Usage in tests

```java

@Test
void shouldProcessOrder() {
  // ... start process, complete tasks ...

  assertThat(processInstance)
    .hasVariableSatisfiesJudge("orderSummary",
      "The order summary should mention the customer name and total price");
}
```

