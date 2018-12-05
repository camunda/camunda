package org.camunda.optimize.service.es.writer;

import org.springframework.stereotype.Component;

@Component
public class CompletedActivityInstanceWriter extends AbstractActivityInstanceWriter {


  protected String createInlineUpdateScript() {
    // new import events should win over already
    // import events, since those might be running
    // activity instances.
    // @formatter:off
    return
      "for (def newEvent : params.events) {" +
        "ctx._source.events.removeIf(item -> item.id.equals(newEvent.id)) ;" +
      "}" +
      "ctx._source.events.addAll(params.events)";
    // @formatter:on
  }

}