/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.protocol.record.intent;

/**
 * The lifecycle of consent: a grant that stands until it is revoked, a one-time approval bound to a
 * process instance, and the requests of the jobs that wait for either.
 */
public enum ConsentIntent implements Intent {
  /** Grant consent for every later request of the subject, until revoked. Distributed. */
  GRANT(0, false),
  /** Revoke a grant. Distributed. */
  REVOKE(1, false),
  /** Answer the consent request of a parked job: approve once, grant, or decline. */
  RESOLVE(2, false),
  /** Hand out the jobs that a grant or an approval released, in batches. */
  RELEASE(3, false),
  GRANTED(4, true),
  REVOKED(5, true),
  APPROVED(6, true),
  DECLINED(7, true),
  /** A job is parked until its subject consents. */
  REQUESTED(8, true),
  RELEASED(9, true);

  private final short value;
  private final boolean isEvent;

  ConsentIntent(final int value, final boolean isEvent) {
    this.value = (short) value;
    this.isEvent = isEvent;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return isEvent;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return GRANT;
      case 1:
        return REVOKE;
      case 2:
        return RESOLVE;
      case 3:
        return RELEASE;
      case 4:
        return GRANTED;
      case 5:
        return REVOKED;
      case 6:
        return APPROVED;
      case 7:
        return DECLINED;
      case 8:
        return REQUESTED;
      case 9:
        return RELEASED;
      default:
        return Intent.UNKNOWN;
    }
  }
}
