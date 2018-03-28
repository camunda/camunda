package org.camunda.optimize.upgrade.to;

/**
 * @author Askar Akhmerov
 */
public class ScriptWrapper {
  private String source;
  private String lang = "painless";

  public ScriptWrapper(String mappingScript) {
    this.source = mappingScript;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getLang() {
    return lang;
  }

  public void setLang(String lang) {
    this.lang = lang;
  }
}
