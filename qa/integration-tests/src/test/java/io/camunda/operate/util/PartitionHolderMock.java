package io.camunda.operate.util;

import io.camunda.operate.zeebe.PartitionHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("test")
@Component
@Primary
public class PartitionHolderMock extends PartitionHolder {

  private boolean mockedPartitions;

  @Override
  public List<Integer> getPartitionIds() {
    final List<Integer> partitions = super.getPartitionIds();
    //if last call was mocked, retry next time
    if (mockedPartitions) {
      partitionIds = new ArrayList<>();
    }
    return partitions;
  }

  @Override
  protected Optional<List<Integer>> getPartitionIdsFromZeebe() {
    final Optional<List<Integer>> partitionIdsFromZeebe = super.getPartitionIdsFromZeebe();
    if (partitionIdsFromZeebe.isEmpty()) {
      mockedPartitions = true;
      return Optional.of(List.of(1, 2));
    } else {
      mockedPartitions = false;
      return partitionIdsFromZeebe;
    }
  }


}
