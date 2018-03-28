package org.camunda.optimize.upgrade.to;

/**
 * @author Askar Akhmerov
 */
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
