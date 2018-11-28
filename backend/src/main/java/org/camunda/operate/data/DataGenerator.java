package org.camunda.operate.data;

public interface DataGenerator {

  void createZeebeDataAsync(boolean manuallyCalled);

}
