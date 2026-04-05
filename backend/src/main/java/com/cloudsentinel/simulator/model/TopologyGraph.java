package com.cloudsentinel.simulator.model;

import java.util.*;

/**
 * Represents the service dependency graph for the cloud topology.
 * Models directed edges: if service A depends on service B,
 * failures in B propagate to A after a configurable delay.
 */
public class TopologyGraph {
    private Map<String, ServiceNode> nodes = new LinkedHashMap<>();
    private Map<String, List<String>> adjacencyList = new LinkedHashMap<>();  // service -> downstream dependents

    public TopologyGraph() {}

    public void addNode(ServiceNode node) {
        nodes.put(node.getServiceId(), node);
        adjacencyList.putIfAbsent(node.getServiceId(), new ArrayList<>());

        // Build reverse adjacency: for each dependency, add this node as a dependent
        for (String dep : node.getUpstreamDependencies()) {
            adjacencyList.computeIfAbsent(dep, k -> new ArrayList<>()).add(node.getServiceId());
        }
    }

    public ServiceNode getNode(String serviceId) {
        return nodes.get(serviceId);
    }

    public Collection<ServiceNode> getAllNodes() {
        return nodes.values();
    }

    /**
     * Returns services that depend on the given service (downstream dependents).
     * When serviceId fails, these services will be affected after propagation delay.
     */
    public List<String> getDownstreamDependents(String serviceId) {
        return adjacencyList.getOrDefault(serviceId, Collections.emptyList());
    }

    /**
     * Returns all services transitively affected by a failure at sourceServiceId,
     * with their propagation depth.
     */
    public Map<String, Integer> getCascadeTargets(String sourceServiceId) {
        Map<String, Integer> targets = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        queue.add(sourceServiceId);
        visited.add(sourceServiceId);
        int depth = 0;

        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                String current = queue.poll();
                if (depth > 0) {
                    targets.put(current, depth);
                }
                for (String dependent : getDownstreamDependents(current)) {
                    if (!visited.contains(dependent)) {
                        visited.add(dependent);
                        queue.add(dependent);
                    }
                }
            }
            depth++;
        }

        return targets;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public void clear() {
        nodes.clear();
        adjacencyList.clear();
    }

    /**
     * Build a simple single-service topology for Level 1 (Simple) difficulty.
     */
    public static TopologyGraph simpleTopology() {
        TopologyGraph graph = new TopologyGraph();
        ServiceNode api = new ServiceNode("api-1", "api-service");
        api.setCpu(0.2);
        api.setMemory(0.3);
        api.setErrorRate(0.0);
        api.setLatency(0.1);
        graph.addNode(api);
        return graph;
    }

    /**
     * Build a medium topology with 3 services for Level 2.
     */
    public static TopologyGraph mediumTopology() {
        TopologyGraph graph = new TopologyGraph();

        ServiceNode api = new ServiceNode("api-1", "api-gateway", List.of("auth-1", "product-1"));
        api.setCpu(0.25); api.setMemory(0.35); api.setErrorRate(0.0); api.setLatency(0.1);

        ServiceNode auth = new ServiceNode("auth-1", "auth-service", List.of("redis-1"));
        auth.setCpu(0.2); auth.setMemory(0.3); auth.setErrorRate(0.0); auth.setLatency(0.08);

        ServiceNode product = new ServiceNode("product-1", "product-service");
        product.setCpu(0.3); product.setMemory(0.4); product.setErrorRate(0.0); product.setLatency(0.12);

        ServiceNode redis = new ServiceNode("redis-1", "redis-cache");
        redis.setCpu(0.15); redis.setMemory(0.25); redis.setErrorRate(0.0); redis.setLatency(0.05);

        graph.addNode(api);
        graph.addNode(auth);
        graph.addNode(product);
        graph.addNode(redis);
        return graph;
    }

    /**
     * Build the full 7-service distributed topology for Level 3.
     */
    public static TopologyGraph complexTopology() {
        TopologyGraph graph = new TopologyGraph();

        ServiceNode gateway = new ServiceNode("api-gateway", "api-gateway", List.of("auth-service", "product-service"));
        gateway.setCpu(0.2); gateway.setMemory(0.3); gateway.setErrorRate(0.0); gateway.setLatency(0.1);

        ServiceNode auth = new ServiceNode("auth-service", "auth-service", List.of("user-db", "redis-cache"));
        auth.setCpu(0.2); auth.setMemory(0.3); auth.setErrorRate(0.0); auth.setLatency(0.08);

        ServiceNode product = new ServiceNode("product-service", "product-service", List.of("product-db", "inventory-service"));
        product.setCpu(0.25); product.setMemory(0.35); product.setErrorRate(0.0); product.setLatency(0.12);

        ServiceNode userDb = new ServiceNode("user-db", "user-db");
        userDb.setCpu(0.3); userDb.setMemory(0.4); userDb.setErrorRate(0.0); userDb.setLatency(0.05);

        ServiceNode productDb = new ServiceNode("product-db", "product-db");
        productDb.setCpu(0.35); productDb.setMemory(0.45); productDb.setErrorRate(0.0); productDb.setLatency(0.06);

        ServiceNode inventory = new ServiceNode("inventory-service", "inventory-service", List.of("product-db"));
        inventory.setCpu(0.2); inventory.setMemory(0.3); inventory.setErrorRate(0.0); inventory.setLatency(0.1);

        ServiceNode redis = new ServiceNode("redis-cache", "redis-cache");
        redis.setCpu(0.15); redis.setMemory(0.25); redis.setErrorRate(0.0); redis.setLatency(0.03);

        graph.addNode(gateway);
        graph.addNode(auth);
        graph.addNode(product);
        graph.addNode(userDb);
        graph.addNode(productDb);
        graph.addNode(inventory);
        graph.addNode(redis);
        return graph;
    }
}
