# Zeebe Test


[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.zeebe/zeebe-test/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.zeebe/zeebe-test)


JUnit test rules for Zeebe applications.

* [Web Site](https://zeebe.io)
* [Documentation](https://docs.zeebe.io)
* [Issue Tracker](https://github.com/zeebe-io/zeebe/issues)
* [Slack Channel](https://zeebe-slackin.herokuapp.com/)
* [User Forum](https://forum.zeebe.io)
* [Contribution Guidelines](/CONTRIBUTING.md)

## Usage example

Add `zeebe-test` as test dependency to your project.

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.zeebe</groupId>
      <artifactId>zb-bom</artifactId>
      <version>${ZEEBE_VERSION}</version>
      <scope>import</scope>
      <type>pom</type>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>io.zeebe</groupId>
    <artifactId>zeebe-broker-core</artifactId>
  </dependency>

  <dependency>
    <groupId>io.zeebe</groupId>
    <artifactId>zeebe-client-java</artifactId>
  </dependency>

  <dependency>
    <groupId>io.zeebe</groupId>
    <artifactId>zeebe-test</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

Use the `ZeebeTestRule` in your test case to start an embedded broker and client.

```java
import java.time.Duration;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.WorkflowInstanceEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WorkflowTest
{
    @Rule
    public final ZeebeTestRule testRule = new ZeebeTestRule();

    private ZeebeClient client;
    private String topic;

    @Before
    public void deploy()
    {
        client = testRule.getClient();
        topic = testRule.getDefaultTopic();

        client.workflows().deploy(topic)
                .resourceFromClasspath("process.bpmn")
                .execute();
    }

    @Test
    public void shouldCompleteWorkflowInstance()
    {
        final WorkflowInstanceEvent workflowInstance = client.workflows().create(topic)
                                                             .bpmnProcessId("process")
                                                             .latestVersion()
                                                             .execute();

        client.tasks().newTaskSubscription(topic)
            .taskType("task")
            .lockOwner("test")
            .lockTime(Duration.ofSeconds(30))
            .handler((c, t) -> c.complete(t).withoutPayload().execute())
            .open();

        testRule.waitUntilWorklowInstanceCompleted(workflowInstance.getWorkflowInstanceKey());
    }

}
```

## Code of Conduct

This project adheres to the Contributor Covenant [Code of
Conduct](/CODE_OF_CONDUCT.md). By participating, you are expected to uphold
this code. Please report unacceptable behavior to code-of-conduct@zeebe.io.

## License

Most Zeebe source files are made available under the [Apache License, Version
2.0](/LICENSE) except for the [broker-core][] component. The [broker-core][]
source files are made available under the terms of the [GNU Affero General
Public License (GNU AGPLv3)][agpl]. See individual source files for
details.

[broker-core]: https://github.com/zeebe-io/zeebe/tree/master/broker-core
[agpl]: https://github.com/zeebe-io/zeebe/blob/master/GNU-AGPL-3.0
