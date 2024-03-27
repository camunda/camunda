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
package io.camunda.operate.util;

public class BackoffIdleStrategy {

  private static final int NOT_IDLE = 0;
  private static final int IDLE = 1;

  private final long baseIdleTime;
  private final float idleIncreaseFactor;
  private final long maxIdleTime;
  private final int maxIdles;

  private int state = NOT_IDLE;
  private int idles;

  public BackoffIdleStrategy(
      final long baseIdleTime, final float idleIncreaseFactor, final long maxIdleTime) {
    this.baseIdleTime = baseIdleTime;
    this.idleIncreaseFactor = idleIncreaseFactor;
    this.maxIdleTime = maxIdleTime;
    maxIdles = calculateMaxIdles();
  }

  private double log(final double base, final double value) {
    return Math.log10(value) / Math.log10(base);
  }

  public void idle() {
    switch (state) {
      case NOT_IDLE:
        state = IDLE;
        idles = 1;
        break;

      default:
        idles++;
        idles = Math.min(idles, maxIdles);
    }
  }

  public void reset() {
    idles = 0;
    state = NOT_IDLE;
  }

  public long idleTime() {
    final long idleTime;

    switch (state) {
      case NOT_IDLE:
        idleTime = 0;
        break;

      default:
        final var t = (long) (baseIdleTime * Math.pow(idleIncreaseFactor, idles - 1));
        idleTime = Math.min(maxIdleTime, t);
    }

    return idleTime;
  }

  private int calculateMaxIdles() {
    if (baseIdleTime <= 0) {
      return 1;
    }
    return ((int) log(idleIncreaseFactor, maxIdleTime / baseIdleTime)) + 1;
  }
}
