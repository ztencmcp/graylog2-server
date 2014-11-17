/*
 * The MIT License
 * Copyright (c) 2012 TORCH GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.graylog2.shared.cluster;

import org.graylog2.plugin.BaseConfiguration;
import org.graylog2.plugin.ServerStatus;
import org.joda.time.DateTime;

import java.io.Serializable;
import java.net.URI;
import java.util.Collections;
import java.util.Set;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class NodeInformation implements Serializable {
    private final String id;
    private final URI transportAddress;
    private final String type;
    private final String shortNodeId;
    private final DateTime lastSeen;
    private final Boolean isMaster;
    private final Set<ServerStatus.Capability> capabilities;

    public NodeInformation(ServerStatus serverStatus, BaseConfiguration configuration) {
        this.transportAddress = configuration.getRestTransportUri();
        this.id = serverStatus.getNodeId().toString();
        this.shortNodeId = id;

        if (serverStatus.hasCapabilities(ServerStatus.Capability.SERVER))
            this.type = "SERVER";
        else {
            if (serverStatus.hasCapabilities(ServerStatus.Capability.RADIO))
                this.type = "RADIO";
            else
                this.type = "UNKNOWN";
        }

        this.isMaster = serverStatus.hasCapability(ServerStatus.Capability.MASTER);

        this.lastSeen = DateTime.now();

        this.capabilities = serverStatus.getCapabilities();
    }

    public String getId() {
        return id;
    }

    public URI getTransportAddress() {
        return transportAddress;
    }

    public String getType() {
        return type;
    }

    public String getShortNodeId() {
        return shortNodeId;
    }

    public DateTime getLastSeen() {
        return lastSeen;
    }

    public Boolean getIsMaster() {
        return isMaster;
    }

    public Set<ServerStatus.Capability> getCapabilities() {
        return Collections.unmodifiableSet(this.capabilities);
    }
}
