/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.system.debug;

import org.graylog2.events.ClusterEvent;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DebugEventHolderTest {
    @Test
    public void setAndGetClusterEventReturnsSameObject() throws Exception {
        ClusterEvent event = ClusterEvent.create("Producer ID", String.class.getCanonicalName(), "Test");
        DebugEventHolder.setClusterEvent(event);

        assertThat(DebugEventHolder.getClusterEvent()).isSameAs(event);
    }

    @Test
    public void setAndGetDebugEventReturnsSameObject() throws Exception {
        DebugEvent event = DebugEvent.create("Node ID", "Test");
        DebugEventHolder.setDebugEvent(event);

        assertThat(DebugEventHolder.getDebugEvent()).isSameAs(event);
    }
}