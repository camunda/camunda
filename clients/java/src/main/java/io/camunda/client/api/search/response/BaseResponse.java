package io.camunda.client.api.search.response;

import io.camunda.client.api.command.ClientException;
import java.util.List;

public interface BaseResponse<T> {

  /** Returns the list of items */
  List<T> items();

  /**
   * Returns the single item or null if the item list is empty
   *
   * @throws ClientException if the items contain more than one entry
   * @return the single item or null if the item list is empty
   */
  default T singleItem() {
    final List<T> items = items();
    if (items.isEmpty()) {
      return null;
    }
    if (items.size() > 1) {
      throw new ClientException("Expecting only one item but got " + items.size());
    }
    return items.get(0);
  }
}
