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
import com.google.common.eventbus.Subscribe;
import org.graylog2.events.ClusterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

import static com.google.common.base.Preconditions.checkNotNull;

public class DebugEventListener {
    private static final Logger LOG = LoggerFactory.getLogger(DebugEventListener.class);

    @Inject
    public DebugEventListener(EventBus serverEventBus,
                              @Named("cluster_event_bus") EventBus clusterEventBus) {
        checkNotNull(serverEventBus).register(this);
        checkNotNull(clusterEventBus).register(this);
    }

    @Subscribe
    public void handleDebugEvent(DebugEvent event) {
        LOG.debug("Received local debug event: {}", event);
        DebugEventHolder.setDebugEvent(event);
    }

    @Subscribe
    public void handleClusterDebugEvent(ClusterEvent event) {
        try {
            Class<?> eventClass = Class.forName(event.eventClass());

            if (DebugEvent.class.isAssignableFrom(eventClass)) {
                LOG.debug("Received cluster debug event: {}", event);
                DebugEventHolder.setClusterEvent(event);
            }
        } catch (ClassNotFoundException e) {
            LOG.debug("Couldn't find event type class", e);
        }
    }
}
