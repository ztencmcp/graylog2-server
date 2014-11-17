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

package org.graylog2.cluster;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.graylog2.plugin.database.validators.Validator;
import org.graylog2.shared.cluster.NodeInformation;
import org.joda.time.DateTime;

import java.util.Map;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class NodeImplHZ implements Node {
    private final NodeInformation nodeInformation;

    public NodeImplHZ(NodeInformation nodeInformation) {
        this.nodeInformation = nodeInformation;
    }

    @Override
    public String getNodeId() {
        return this.nodeInformation.getId();
    }

    @Override
    public boolean isMaster() {
        return this.nodeInformation.getIsMaster();
    }

    @Override
    public String getTransportAddress() {
        return this.nodeInformation.getTransportAddress().toString();
    }

    @Override
    public DateTime getLastSeen() {
        return this.nodeInformation.getLastSeen();
    }

    @Override
    public String getShortNodeId() {
        return this.nodeInformation.getShortNodeId();
    }

    @Override
    public Type getType() {
        return Type.fromString(this.nodeInformation.getType());
    }

    @Override
    public String getId() {
        return this.nodeInformation.getId();
    }

    @Override
    @JsonIgnore
    public Map<String, Object> getFields() {
        return null;
    }

    @Override
    @JsonIgnore
    public Map<String, Validator> getValidations() {
        return null;
    }

    @Override
    @JsonIgnore
    public Map<String, Validator> getEmbeddedValidations(String key) {
        return null;
    }

    @Override
    @JsonIgnore
    public Map<String, Object> asMap() {
        return null;
    }
}
