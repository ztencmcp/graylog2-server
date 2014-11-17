/**
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.shared.bindings.providers;

import com.hazelcast.config.Config;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.config.MemberAttributeConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.graylog2.plugin.BaseConfiguration;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.shared.cluster.NodeInformation;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class HazelcastInstanceProvider implements Provider<HazelcastInstance> {
    private final BaseConfiguration configuration;
    private final ServerStatus serverStatus;

    @Inject
    public HazelcastInstanceProvider(BaseConfiguration configuration, ServerStatus ServerStatus) {
        this.configuration = configuration;
        serverStatus = ServerStatus;
    }

    @Override
    public HazelcastInstance get() {
        Config config = new Config(serverStatus.getNodeId().toString());
        config.setProperty( "hazelcast.logging.type", "slf4j");

        final GroupConfig groupConfig = config.getGroupConfig();
        groupConfig.setName(configuration.getClusterName());
        groupConfig.setPassword(configuration.getClusterPassword());

        MemberAttributeConfig memberAttributeConfig = new MemberAttributeConfig();

        NodeInformation nodeInformation = new NodeInformation(serverStatus, configuration);

        Map<String, Object> map = new HashMap<>();
        map.put("node_information", nodeInformation);

        memberAttributeConfig.setAttributes(map);

        config.setMemberAttributeConfig(memberAttributeConfig);
        HazelcastInstance instance = Hazelcast.newHazelcastInstance(config);

        return instance;
    }
}
