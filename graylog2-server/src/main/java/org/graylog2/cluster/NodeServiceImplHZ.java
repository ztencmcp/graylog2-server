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

import com.beust.jcommander.internal.Maps;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;
import org.graylog2.database.ValidationException;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.database.Persisted;
import org.graylog2.plugin.database.validators.ValidationResult;
import org.graylog2.plugin.database.validators.Validator;
import org.graylog2.plugin.system.NodeId;
import org.graylog2.shared.cluster.NodeInformation;

import javax.inject.Inject;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class NodeServiceImplHZ implements NodeService {
    private static final String CK_NODE_INFORMATION_KEY = "node_information";

    private final HazelcastInstance hazelCastInstance;

    @Inject
    public NodeServiceImplHZ(HazelcastInstance hazelCastInstance) {
        this.hazelCastInstance = hazelCastInstance;
    }

    @Override
    public String registerServer(String nodeId, boolean isMaster, URI restTransportUri) {
        return nodeId;
    }

    @Override
    public String registerRadio(String nodeId, String restTransportUri) {
        return nodeId;
    }

    private Set<NodeInformation> getNodeInformations() {
        Set<NodeInformation> result = new HashSet<>();
        for (Member member : this.hazelCastInstance.getCluster().getMembers()) {
            Object rawNodeInformation = member.getAttributes().get(CK_NODE_INFORMATION_KEY);

            if (rawNodeInformation != null && rawNodeInformation instanceof NodeInformation)
                result.add((NodeInformation)rawNodeInformation);
        }

        return result;
    }

    @Override
    public Node byNodeId(String nodeId) throws NodeNotFoundException {
        for (NodeInformation nodeInformation : getNodeInformations())
            if (nodeInformation.getId().equals(nodeId))
                return new NodeImplHZ(nodeInformation);
        return null;
    }

    @Override
    public Node byNodeId(NodeId nodeId) throws NodeNotFoundException {
        return byNodeId(nodeId.toString());
    }

    @Override
    public Map<String, Node> allActive(Node.Type type) {
        Map<String, Node> result = new HashMap<>();

        for (NodeInformation nodeInformation : getNodeInformations())
            if (nodeInformation.getType().equals(type.toString()))
                result.put(nodeInformation.getId(), new NodeImplHZ(nodeInformation));

        return result;
    }

    @Override
    public Map<String, Node> allActive() {
        Map<String, Node> result = new HashMap<>();

        for (NodeInformation nodeInformation : getNodeInformations())
            result.put(nodeInformation.getId(), new NodeImplHZ(nodeInformation));

        return result;
    }

    @Override
    public void dropOutdated() {

    }

    @Override
    public void markAsAlive(Node node, boolean isMaster, String restTransportAddress) {

    }

    @Override
    public void markAsAlive(Node node, boolean isMaster, URI restTransportAddress) {

    }

    @Override
    public boolean isOnlyMaster(NodeId nodeIde) {
        for (NodeInformation nodeInformation : getNodeInformations())
            if (nodeInformation.getCapabilities().contains(ServerStatus.Capability.MASTER) && !nodeInformation.getId().equals(nodeIde.toString()))
                return false;

        return true;
    }

    @Override
    public boolean isAnyMasterPresent() {
        for (NodeInformation nodeInformation : getNodeInformations())
            if (nodeInformation.getCapabilities().contains(ServerStatus.Capability.MASTER))
                return true;

        return false;
    }

    @Override
    public <T extends Persisted> int destroy(T model) {
        return 0;
    }

    @Override
    public <T extends Persisted> int destroyAll(Class<T> modelClass) {
        return 0;
    }

    @Override
    public <T extends Persisted> String save(T model) throws ValidationException {
        return model.getId();
    }

    @Override
    public <T extends Persisted> String saveWithoutValidation(T model) {
        return model.getId();
    }

    @Override
    public <T extends Persisted> Map<String, List<ValidationResult>> validate(T model, Map<String, Object> fields) {
        return Maps.newHashMap();
    }

    @Override
    public <T extends Persisted> Map<String, List<ValidationResult>> validate(T model) {
        return Maps.newHashMap();
    }

    @Override
    public Map<String, List<ValidationResult>> validate(Map<String, Validator> validators, Map<String, Object> fields) {
        return Maps.newHashMap();
    }
}
