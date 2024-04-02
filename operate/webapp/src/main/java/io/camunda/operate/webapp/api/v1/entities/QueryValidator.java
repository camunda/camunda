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

import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ValidationException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class QueryValidator<T> {

  public static final int MAX_QUERY_SIZE = 1000;
  private List<String> fields;

  public void validate(final Query<T> query, final Class<T> queriedClass)
      throws ValidationException {
    validate(query, queriedClass, null);
  }

  public void validate(
      final Query<T> query,
      final Class<T> queriedClass,
      final CustomQueryValidator<T> customValidator) {
    retrieveFieldsFor(queriedClass);
    validateSorting(query.getSort(), fields);
    validatePaging(query);
    if (customValidator != null) {
      customValidator.validate(query);
    }
  }

  private void retrieveFieldsFor(final Class<T> queriedClass) {
    if (fields == null) {
      fields =
          Arrays.stream(queriedClass.getDeclaredFields())
              .map(Field::getName)
              .collect(Collectors.toList());
    }
  }

  protected void validatePaging(final Query<T> query) {
    final int size = query.getSize();
    if (size <= 0 || size > MAX_QUERY_SIZE) {
      throw new ClientException("size should be greater than zero and less than " + MAX_QUERY_SIZE);
    }
    final Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null && searchAfter.length == 0) {
      throw new ValidationException("searchAfter should have a least 1 value");
    }
    if (query.getSort() != null) {
      final int sortSize = query.getSort().size();
      if (searchAfter != null && searchAfter.length != sortSize + 1) {
        throw new ValidationException(
            String.format("searchAfter should have a %s values", sortSize + 1));
      }
    }
  }

  protected void validateSorting(final List<Sort> sortSpecs, final List<String> fields) {
    if (sortSpecs == null || sortSpecs.isEmpty()) {
      return;
    }
    final List<String> givenFields =
        CollectionUtil.withoutNulls(
            sortSpecs.stream().map(Sort::getField).collect(Collectors.toList()));
    if (givenFields.isEmpty()) {
      throw new ValidationException(
          "No 'field' given in sort. Example: \"sort\": [{\"field\":\"name\",\"order\": \"ASC\"}] ");
    }
    final List<String> invalidSortSpecs = getInvalidFields(fields, givenFields);
    if (!invalidSortSpecs.isEmpty()) {
      throw new ValidationException(
          String.format("Sort has invalid field(s): %s", String.join(", ", invalidSortSpecs)));
    }
  }

  private List<String> getInvalidFields(
      final List<String> availableFields, final List<String> givenFields) {
    return givenFields.stream()
        .filter(field -> !availableFields.contains(field))
        .collect(Collectors.toList());
  }

  public interface CustomQueryValidator<T> {
    void validate(Query<T> query) throws ValidationException;
  }
}
