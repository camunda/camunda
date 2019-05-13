/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system.management.deployment;

import io.zeebe.clustering.management.MessageHeaderDecoder;
import io.zeebe.clustering.management.MessageHeaderEncoder;
import io.zeebe.clustering.management.NotLeaderResponseDecoder;
import io.zeebe.clustering.management.NotLeaderResponseEncoder;
import io.zeebe.engine.util.SbeBufferWriterReader;

public class NotLeaderResponse
    extends SbeBufferWriterReader<NotLeaderResponseEncoder, NotLeaderResponseDecoder> {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final NotLeaderResponseEncoder bodyEncoder = new NotLeaderResponseEncoder();

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final NotLeaderResponseDecoder bodyDecoder = new NotLeaderResponseDecoder();

  @Override
  protected NotLeaderResponseEncoder getBodyEncoder() {
    return bodyEncoder;
  }

  @Override
  protected NotLeaderResponseDecoder getBodyDecoder() {
    return bodyDecoder;
  }
}
