package org.camunda.optimize.service.es.writer;

import org.springframework.stereotype.Component;

@Component
public class RunningActivityInstanceWriter extends AbstractActivityInstanceWriter {

  protected String createInlineUpdateScript() {
    // already imported events should win over the
    // new instances, since the stored instances are
    // probably completed activity instances.
    // @formatter:off
    return
      "for (def oldEvent : ctx._source.events) {" +
        "params.events.removeIf(item -> item.id.equals(oldEvent.id));" +
      "}" +
      "ctx._source.events.addAll(params.events)";
    // @formatter:on
  }

}