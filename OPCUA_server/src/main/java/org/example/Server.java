/*
 * Copyright (c) 2019 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.example;

import static java.lang.Thread.sleep;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.*;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;

public class Server {

    public static void main(String[] args) throws Exception {
        Server server = new Server();

        server.startup().get();

        while(true) {
            sleep(1000); // sleep 1s
        }
    }

    private final OpcUaServer server;

    public Server() throws Exception {

        Set<EndpointConfiguration> endpointConfigurations = createEndpointConfigurations();

        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
            .setApplicationUri("urn:sdu:milo:server")
            .setApplicationName(LocalizedText.english("Milo OPC UA Server"))
            .setEndpoints(endpointConfigurations)
            .setBuildInfo(
                new BuildInfo(
                    "urn:sdu:milo:server",
                    "sdu",
                    "milo server",
                    OpcUaServer.SDK_VERSION,
                    "", DateTime.now()))
            .setProductUri("urn:sdu:milo:server")
            .build();

        server = new OpcUaServer(serverConfig);

        Namespace namespace = new Namespace(server);
        namespace.startup();
    }

    private Set<EndpointConfiguration> createEndpointConfigurations() {
        Set<EndpointConfiguration> endpointConfigurations = new LinkedHashSet<>();

        EndpointConfiguration.Builder builder = EndpointConfiguration.newBuilder()
                .setBindAddress("localhost")
                .setHostname(HostnameUtil.getHostname())
                .setPath("/milo")
                .addTokenPolicies(USER_TOKEN_POLICY_ANONYMOUS); //anonymous login only

        EndpointConfiguration.Builder noSecurityBuilder = builder.copy()
                .setSecurityPolicy(SecurityPolicy.None)
                .setSecurityMode(MessageSecurityMode.None)
                .setTransportProfile(TransportProfile.TCP_UASC_UABINARY) //opc.tcp only
                .setBindPort(12686);

        endpointConfigurations.add(noSecurityBuilder.build());

        return endpointConfigurations;
    }

    public OpcUaServer getServer() {
        return server;
    }

    public CompletableFuture<OpcUaServer> startup() {
        return server.startup();
    }

    public CompletableFuture<OpcUaServer> shutdown() {
        return server.shutdown();
    }

}
