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
package io.camunda.operate.zeebeimport.cache;

public interface TreePathCache {

  /**
   * Resolve the parent treePath for the given {@link FNITreePathCacheCompositeKey}, which contains
   * information to the corresponding flow node instance.
   *
   * <p>When the flow scope and process instance key are equal, the PI key is returned as treePath,
   * as it corresponds to a FNI on root level.
   *
   * <p>When the cache doesn't contain (as it might be evicted) the parent treePath for a given flow
   * node instance, the implementation should be able to resolve it somehow else.
   *
   * <p>If the treePath can't be resolved the process instance key should be returned.
   *
   * @param compositeKey a composite key that contains information used to resolve the parent tree
   *     path for the flow node instance
   * @return the parent treePath of the flow node instance
   * @throws IllegalArgumentException when the flow node instance record doesn't correspond to a
   *     supported partition
   */
  String resolveParentTreePath(final FNITreePathCacheCompositeKey compositeKey);

  /**
   * Cache the treePath together with the given key part of the {@link
   * FNITreePathCacheCompositeKey}.
   *
   * <p>Used for flow node instances that are a container element, like process, sub-process, event
   * sub-process, etc. This allows to resolve the treePath as parent treePath later on corresponding
   * children's.
   *
   * @param compositeKey a composite key that contains information of flow node instance
   * @param treePath the treePath of the flow node instance
   */
  void cacheTreePath(FNITreePathCacheCompositeKey compositeKey, String treePath);
}
