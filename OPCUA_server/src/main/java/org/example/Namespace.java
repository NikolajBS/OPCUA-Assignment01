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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.math.Quantiles;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespace;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.api.nodes.VariableNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.BaseEventNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.ServerNode;
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.delegates.AttributeDelegate;
import org.eclipse.milo.opcua.sdk.server.nodes.delegates.AttributeDelegateChain;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

public class Namespace extends ManagedNamespace {

    static final String NAMESPACE_URI = "urn:sdu:milo:ICPS";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Random random = new Random();

    private final SubscriptionModel subscriptionModel;

    Namespace(OpcUaServer server) {
        super(server, NAMESPACE_URI);

        subscriptionModel = new SubscriptionModel(server, this);
    }

    @Override
    protected void onStartup() {
        super.onStartup();

        // Create a "ICPS" main folder and add it to the node manager
        NodeId folderNodeId = newNodeId("ICPS");

        UaFolderNode folderNode = new UaFolderNode(
            getNodeContext(),
            folderNodeId,
            newQualifiedName("ICPS"),
            LocalizedText.english("ICPS")
        );

        getNodeManager().addNode(folderNode);

        // Make sure our new folder shows up under the server's Objects folder.
        folderNode.addReference(new Reference(
            folderNode.getNodeId(),
            Identifiers.Organizes,
            Identifiers.ObjectsFolder.expanded(),
            false
        ));

        // Add the rest of the nodes
        addVariableNodes(folderNode);


        // Set the EventNotifier bit on Server Node for Events.
        UaNode serverNode = getServer()
            .getAddressSpaceManager()
            .getManagedNode(Identifiers.Server)
            .orElse(null);

        if (serverNode instanceof ServerNode) {
            ((ServerNode) serverNode).setEventNotifier(ubyte(1));

            // Post a bogus Event every couple seconds
            getServer().getScheduledExecutorService().scheduleAtFixedRate(() -> {
                try {
                    BaseEventNode eventNode = getServer().getEventFactory().createEvent(
                        newNodeId(UUID.randomUUID()),
                        Identifiers.BaseEventType
                    );

                    eventNode.setBrowseName(new QualifiedName(1, "foo"));
                    eventNode.setDisplayName(LocalizedText.english("foo"));
                    eventNode.setEventId(ByteString.of(new byte[]{0, 1, 2, 3}));
                    eventNode.setEventType(Identifiers.BaseEventType);
                    eventNode.setSourceNode(serverNode.getNodeId());
                    eventNode.setSourceName(serverNode.getDisplayName().getText());
                    eventNode.setTime(DateTime.now());
                    eventNode.setReceiveTime(DateTime.NULL_VALUE);
                    eventNode.setMessage(LocalizedText.english("event message!"));
                    eventNode.setSeverity(ushort(2));

                    getServer().getEventBus().post(eventNode);

                    eventNode.delete();
                } catch (Throwable e) {
                    logger.error("Error creating EventNode: {}", e.getMessage(), e);
                }
            }, 0, 2, TimeUnit.SECONDS);
        }
    }

    private void addVariableNodes(UaFolderNode rootNode) {
        //addArrayNodes(rootNode);
        //addStatic(rootNode);
        //addAdminReadableNodes(rootNode);
        //addAdminWritableNodes(rootNode);
        //addDynamic(rootNode);
        //addDataAccessNodes(rootNode);
        //addWriteOnlyNodes(rootNode);
        addFolder(rootNode);
        //addWindowSensor(rootNode);
        System.out.println(String.valueOf(getAPIData(1, "alarm")));
        //System.out.println("The id for Window sensor is " + getDeviceID("Window Sensor"));
        //getValue();
        addWindowSensor(rootNode);
        addSmartplug(rootNode);
        nodeSubscription();
        //sendData(true);
    }

    private void addStatic(UaFolderNode rootNode) {
        UaFolderNode scalarTypesFolder = new UaFolderNode(
            getNodeContext(),
            newNodeId("ICPS/Static"),
            newQualifiedName("Static"),
            LocalizedText.english("Static")
        );

        getNodeManager().addNode(scalarTypesFolder);
        rootNode.addOrganizes(scalarTypesFolder);

        String name = "Int32";
        NodeId typeId = Identifiers.Int32;
        Variant variant = new Variant(uint(32));

        UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
            .setNodeId(newNodeId("ICPS/Static/" + name))
            .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
            .setUserAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
            .setBrowseName(newQualifiedName(name))
            .setDisplayName(LocalizedText.english(name))
            .setDataType(typeId)
            .setTypeDefinition(Identifiers.BaseDataVariableType)
            .build();

        node.setValue(new DataValue(variant));

        node.setAttributeDelegate(new ValueLoggingDelegate());

        getNodeManager().addNode(node);
        scalarTypesFolder.addOrganizes(node);
    }

    private void addDynamic(UaFolderNode rootNode) {
        UaFolderNode dynamicFolder = new UaFolderNode(
            getNodeContext(),
            newNodeId("ICPS/Dynamic"),
            newQualifiedName("Dynamic"),
            LocalizedText.english("Dynamic")
        );

        getNodeManager().addNode(dynamicFolder);
        rootNode.addOrganizes(dynamicFolder);

        // Dynamic Int32
        {
            String name = "Int32";
            NodeId typeId = Identifiers.Int32;
            Variant variant = new Variant(0);

            UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId("ICPS/Dynamic/" + name))
                .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setDataType(typeId)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();

            node.setValue(new DataValue(variant));

            AttributeDelegate delegate = AttributeDelegateChain.create(
                new AttributeDelegate() {
                    @Override
                    public DataValue getValue(AttributeContext context, VariableNode node) throws UaException {
                        return new DataValue(new Variant(random.nextInt(100)));
                    }
                },
                ValueLoggingDelegate::new
            );

            node.setAttributeDelegate(delegate);

            getNodeManager().addNode(node);
            dynamicFolder.addOrganizes(node);
        }

    }

    private int getDeviceID(String name){ // TODO fix later
        String GET_URL = "http://gw-6d26.sandbox.tek.sdu.dk/ssapi/zb/dev";
        URL obj = null;
        HttpURLConnection con = null;
        int responseCode = 0;
        int id = -1;
        JSONObject json = null;
        try {
            obj = new URL(GET_URL);
            con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            //con.setRequestProperty("User-Agent", USER_AGENT);
            responseCode = con.getResponseCode();// Response code 200 or 201 is success, otherwise there is a problem.
            System.out.println("GET Response Code :: " + responseCode);
        } catch (IOException e){
            e.printStackTrace();
        }


        try {
            if (responseCode == HttpURLConnection.HTTP_OK) { // success
                BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = reader.readLine()) != null) {
                    response.append(inputLine);
                }
                reader.close();
                json = new JSONObject(response);

                //System.out.println(json.getJSONArray());
                id = json.getInt("id");

            } else {
                System.out.println("GET failed");
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
        return id;
    }

    private JSONArray getAPIData(int id, String key){
        String GET_URL = "http://gw-6d26.sandbox.tek.sdu.dk/ssapi/zb/dev/"+id+"/ldev/"+key+"/data";
        URL obj = null;
        HttpURLConnection con = null;
        int responseCode = 0;
        JSONArray json = null;
        try {
            obj = new URL(GET_URL);
            con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            //con.setRequestProperty("User-Agent", USER_AGENT);
            responseCode = con.getResponseCode();// Response code 200 or 201 is success, otherwise there is a problem.
            System.out.println("GET Response Code :: " + responseCode);
        } catch (IOException e){
            e.printStackTrace();
        }


        try {
            if (responseCode == HttpURLConnection.HTTP_OK) { // success
                BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = reader.readLine()) != null) {
                    response.append(inputLine);
                }
                reader.close();
                json = new JSONArray(String.valueOf(response));
            } else {
                System.out.println("GET failed");
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
        return json;
    }

    private void sendData(Boolean onOff) {
        String PUT_URL = "http://gw-6d26.sandbox.tek.sdu.dk/ssapi/zb/dev/4/ldev/smartplug/data/onoff";
        String json ="{\"value\": " + onOff + "}";
        URL obj = null;
        HttpURLConnection con = null;
        int responseCode = 0;
        try {
            obj = new URL(PUT_URL);
            con = (HttpURLConnection) obj.openConnection();
            con.setRequestProperty("Content-Type", "application/json;");
            con.setDoOutput(true);
            con.setRequestMethod("PUT");
            OutputStream output = con.getOutputStream();
            output.write(json.getBytes());
            output.close();
            responseCode = con.getResponseCode();
            System.out.println("PUT Response Code :: " + responseCode);
        } catch (IOException e) {
            e.printStackTrace();
        }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("PUT SUCCESS");
            } else {
                System.out.println("PUT failed " + responseCode);
            }
        }


    private String getValue(int id, String key){
        JSONArray json = new JSONArray();
        json = getAPIData(id,key);
        return String.valueOf(json.getJSONObject(0).getBoolean("value"));

    }

    private void addFolder(UaFolderNode rootNode) {
        UaFolderNode scalarTypesFolder = new UaFolderNode(
                getNodeContext(),
                newNodeId("ICPS/nodeDevices"),
                newQualifiedName("nodeDevices"),
                LocalizedText.english("nodeDevices")
        );

        getNodeManager().addNode(scalarTypesFolder);
        rootNode.addOrganizes(scalarTypesFolder);

        String name = "Integer";
        NodeId typeId = Identifiers.Integer;
        Variant variant = new Variant(1);

        UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId("ICPS/Static/" + name)) //"ICPS/myFolder/" + name
                .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_ONLY)))
                .setUserAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_ONLY)))
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setDataType(typeId)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();

        node.setValue(new DataValue(variant));

        node.setAttributeDelegate(new ValueLoggingDelegate());
        getNodeManager().addNode(node);
        scalarTypesFolder.addOrganizes(node);
    }

    private void addWindowSensor(UaFolderNode rootNode)  {
        UaFolderNode scalarTypesFolder = new UaFolderNode(
                getNodeContext(),
                newNodeId("ICPS/nodeDevices"),
                newQualifiedName("nodeDevices"),
                LocalizedText.english("nodeDevices")
        );

        getNodeManager().addNode(scalarTypesFolder);
        rootNode.addOrganizes(scalarTypesFolder);

        String name = "Window sensor";
        NodeId typeId = Identifiers.Boolean;
        Variant variant = new Variant(Boolean.valueOf(getValue(1, "alarm")));

        UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId("ICPS/nodeDevices/" + name))
                .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.CurrentRead)))
                .setUserAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.CurrentRead)))
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setDataType(typeId)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();

        node.setValue(new DataValue(variant));

        node.setAttributeDelegate(new ValueLoggingDelegate());

        getNodeManager().addNode(node);
        scalarTypesFolder.addOrganizes(node);

    }
    private void addAirQualitySensor(UaFolderNode rootNode){

    }

    private void addMotionSensor(UaFolderNode rootNode){

    }
    private void addMultiSensor(UaFolderNode rootNode){

    }

    private void addSmartplug(UaFolderNode rootNode){
        UaFolderNode scalarTypesFolder = new UaFolderNode(
                getNodeContext(),
                newNodeId("ICPS/nodeDevices"),
                newQualifiedName("nodeDevices"),
                LocalizedText.english("nodeDevices")
        );

        getNodeManager().addNode(scalarTypesFolder);
        rootNode.addOrganizes(scalarTypesFolder);

        String name = "Smart plug";
        NodeId typeId = Identifiers.Boolean;
        Variant variant = new Variant(Boolean.valueOf(getValue(4, "smartplug")));

        UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId("ICPS/nodeDevices/" + name))
                .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.CurrentRead, AccessLevel.CurrentWrite)))
                .setUserAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.CurrentRead, AccessLevel.CurrentWrite)))
                .setBrowseName(newQualifiedName(name))
                .setDisplayName(LocalizedText.english(name))
                .setDataType(typeId)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();

        node.setValue(new DataValue(variant));

        node.setAttributeDelegate(new ValueLoggingDelegate());

        getNodeManager().addNode(node);
        scalarTypesFolder.addOrganizes(node);
    }

    private void nodeSubscription() {
        new Thread(() -> {
            String previousValue = getValue(1, "alarm");

            // Window Sensor
            NodeId windowNodeId  = new NodeId(2, "ICPS/nodeDevices/Window sensor");
            UaVariableNode windowNode = (UaVariableNode) getNodeManager().get(windowNodeId);

            // Smart plug
            NodeId smartNodeId  = new NodeId(2, "ICPS/nodeDevices/Smart plug");
            UaVariableNode smartplugNode = (UaVariableNode) getNodeManager().get(smartNodeId);

            while (true) {
                windowNode.setValue(new DataValue(new Variant(getValue(1,"alarm"))));
                System.out.println("current value for smart plug " + smartplugNode.getValue());

                if(smartplugNode.getValue().toString().contains("true")){
                    sendData(true);
                }
                else if (smartplugNode.getValue().toString().contains("false")){
                    sendData(false);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }).start();
    }





    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsCreated(dataItems);
    }

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) { subscriptionModel.onDataItemsModified(dataItems); }

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsDeleted(dataItems);
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }

}
