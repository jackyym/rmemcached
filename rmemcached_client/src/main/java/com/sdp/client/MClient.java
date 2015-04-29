package com.sdp.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentMap;

import org.jboss.netty.util.internal.ConcurrentHashMap;

import com.sdp.server.ServerNode;
/**
 * 
 * @author martji
 *
 */

public class MClient {

	int clientId;
	Map<Integer, RMemcachedClientImpl> clientMap;
	Vector<Integer> clientIdVector;
	/**
	 * keyReplicaMap : save the replicated key and the replica
	 */
	ConcurrentMap<String, Integer> keyReplicaMap;
	
	/**
	 * 
	 * @param clientId
	 * @param serversMap
	 */
	public MClient(int clientId, Map<Integer, ServerNode> serversMap) {
		this(clientId);
		init(serversMap);
	}
	
	public MClient(int clientId) {
		this.clientId = clientId;
		clientMap = new HashMap<Integer, RMemcachedClientImpl>();
		keyReplicaMap = new ConcurrentHashMap<String, Integer>();
		clientIdVector = new Vector<Integer>();
	}
	
	public void init(Map<Integer, ServerNode> serversMap) {
		Collection<ServerNode> serverList = serversMap.values();
		for (ServerNode serverNode : serverList) {
			int serverId = serverNode.getId();
			RMemcachedClientImpl rmClient = new RMemcachedClientImpl(serverNode);
			rmClient.bindKeyReplicaMap(keyReplicaMap);
			clientMap.put(serverId, rmClient);
			clientIdVector.add(serverId);
		}
	}
	
	public void shutdown() {
		Collection<RMemcachedClientImpl> clientList = clientMap.values();
		for (RMemcachedClientImpl mClient : clientList) {
			mClient.shutdown();
		}
	}
	
	public int leaderClient(String key) {
		int leaderIndex = gethashMem(key);
		return clientIdVector.get(leaderIndex);
	}
	
	public int gethashMem(String key) {
		int clientsNum = clientIdVector.size();
		if (clientsNum == 1) {
			return 0;
		}
		int leaderIndex = key.hashCode() % clientsNum;
		leaderIndex = Math.abs(leaderIndex);
		return leaderIndex;
	}
	
	public String get(String key) {
		String value = null;
		RMemcachedClientImpl rmClient;
		int masterId = leaderClient(key);
		if (keyReplicaMap.containsKey(key)) {
			int replicasId = keyReplicaMap.get(key);
			rmClient = clientMap.get(replicasId);
			value = rmClient.get(key, masterId == replicasId);
			if (value == null | value.length() == 0) {
				rmClient = clientMap.get(masterId);
				value = rmClient.asynGet(key, replicasId);
			}
		} else {
			rmClient = clientMap.get(masterId);
			value = rmClient.get(key, true);
		}
		return value;
	}
	
	public boolean set(String key, String value) {
		boolean result = false;
		RMemcachedClientImpl rmClient = clientMap.get(leaderClient(key));
		result = rmClient.set(key, value);
		return result;
	}
}
