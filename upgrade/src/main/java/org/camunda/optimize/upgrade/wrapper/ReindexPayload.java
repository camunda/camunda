/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.wrapper;


public class ReindexPayload {
  private SourceWrapper source;
  private DestinationWrapper dest;
  private ScriptWrapper script;

  public SourceWrapper getSource() {
    return source;
  }

  public void setSource(SourceWrapper source) {
    this.source = source;
  }

  public DestinationWrapper getDest() {
    return dest;
  }

  public void setDest(DestinationWrapper dest) {
    this.dest = dest;
  }

  public ScriptWrapper getScript() {
    return script;
  }

  public void setScript(ScriptWrapper script) {
    this.script = script;
  }
}
