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

import com.google.common.eventbus.EventBus;
import org.graylog2.events.ClusterEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class DebugEventListenerTest {
    @Spy
    private EventBus serverEventBus;
    @Spy
    private EventBus clusterEventBus;

    private DebugEventListener listener;

    @Before
    public void setUp() {
        DebugEventHolder.setClusterEvent(null);
        DebugEventHolder.setDebugEvent(null);
        listener = new DebugEventListener(serverEventBus, clusterEventBus);
    }

    @Test
    public void testHandleDebugEvent() throws Exception {
        DebugEvent event = DebugEvent.create("Node ID", "Test");
        assertThat(DebugEventHolder.getDebugEvent()).isNull();
        serverEventBus.post(event);
        assertThat(DebugEventHolder.getDebugEvent()).isSameAs(event);
    }

    @Test
    public void testHandleClusterDebugEvent() throws Exception {
        DebugEvent event = DebugEvent.create("Node ID", "Test");
        ClusterEvent clusterEvent = ClusterEvent.create("Producer ID", DebugEvent.class.getCanonicalName(), event);
        assertThat(DebugEventHolder.getClusterEvent()).isNull();
        clusterEventBus.post(clusterEvent);
        assertThat(DebugEventHolder.getClusterEvent()).isSameAs(clusterEvent);
    }

    @Test
    public void testHandleClusterDebugEventWithInheritedClass() throws Exception {
        ClusterEvent clusterEvent = ClusterEvent.create("Producer ID", InheritedDebugEvent.class.getCanonicalName(), new InheritedDebugEvent());
        assertThat(DebugEventHolder.getClusterEvent()).isNull();
        clusterEventBus.post(clusterEvent);
        assertThat(DebugEventHolder.getClusterEvent()).isSameAs(clusterEvent);
    }

    @Test
    public void testHandleClusterDebugEventWithIncompatibleClass() throws Exception {
        ClusterEvent clusterEvent = ClusterEvent.create("Producer ID", String.class.getCanonicalName(), "Test");
        assertThat(DebugEventHolder.getClusterEvent()).isNull();
        clusterEventBus.post(clusterEvent);
        assertThat(DebugEventHolder.getClusterEvent()).isNull();
    }
}