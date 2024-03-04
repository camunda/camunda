/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.api.v1.entities;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Query<T> {

  private T filter;
  private int size = 10;
  // search_after paging method
  private Object[] searchAfter = null;
  private List<Sort> sort = null;

  public Query() {
    super();
  }

  public int getSize() {
    return size;
  }

  public Query<T> setSize(final int size) {
    this.size = size;
    return this;
  }

  public Object[] getSearchAfter() {
    return searchAfter;
  }

  public Query<T> setSearchAfter(final Object[] searchAfter) {
    this.searchAfter = searchAfter;
    return this;
  }

  public List<Sort> getSort() {
    return sort;
  }

  public Query<T> setSort(final List<Sort> sort) {
    this.sort = sort;
    return this;
  }

  public T getFilter() {
    return filter;
  }

  public Query<T> setFilter(final T filter) {
    this.filter = filter;
    return this;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(filter, size, sort);
    result = 31 * result + Arrays.hashCode(searchAfter);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Query<?> query = (Query<?>) o;
    return size == query.size
        && Objects.equals(filter, query.filter)
        && Arrays.equals(searchAfter, query.searchAfter)
        && Objects.equals(sort, query.sort);
  }

  @Override
  public String toString() {
    return "Query{"
        + "filter="
        + filter
        + ", size="
        + size
        + ", searchAfter="
        + Arrays.toString(searchAfter)
        + ", sort="
        + sort
        + '}';
  }

  public static class Sort {

    private String field;
    private Order order = Order.ASC;

    public static Sort of(String field, Order order) {
      return new Sort().setField(field).setOrder(order);
    }

    public static Sort of(String field) {
      return of(field, Order.ASC);
    }

    public static List<Sort> listOf(String field, Order order) {
      return List.of(of(field, order));
    }

    public static List<Sort> listOf(String field) {
      return List.of(of(field));
    }

    public String getField() {
      return field;
    }

    public Sort setField(final String field) {
      this.field = field;
      return this;
    }

    public Order getOrder() {
      return order;
    }

    public Sort setOrder(final Order order) {
      this.order = order;
      return this;
    }

    @Override
    public int hashCode() {
      return Objects.hash(field, order);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Sort sort = (Sort) o;
      return Objects.equals(field, sort.field) && order == sort.order;
    }

    public enum Order {
      ASC,
      DESC
    }
  }
}
