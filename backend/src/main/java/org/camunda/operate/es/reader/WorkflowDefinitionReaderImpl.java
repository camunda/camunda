package org.camunda.operate.es.reader;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@Profile("elasticsearch")
public class WorkflowDefinitionReaderImpl implements WorkflowDefinitionReader {
}
