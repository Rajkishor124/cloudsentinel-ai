/**
 * Topology page with D3 force-directed graph visualization.
 */

import { useEffect, useRef, useState } from 'react';
import * as d3 from 'd3';
import { useSimulationStore } from '@stores/simulationStore';
import Card from '@components/ui/Card';
import Badge from '@components/ui/Badge';
import LoadingSpinner from '@components/ui/LoadingSpinner';
import Button from '@components/ui/Button';
import type { ServiceNode } from '@types/api';

export default function TopologyPage() {
  const { topology, fetchTopology, isLoading } = useSimulationStore();
  const svgRef = useRef<SVGSVGElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [selectedNode, setSelectedNode] = useState<ServiceNode | null>(null);

  useEffect(() => {
    fetchTopology();
  }, []);

  useEffect(() => {
    if (!topology || !svgRef.current || !containerRef.current) return;

    const container = containerRef.current;
    const width = container.clientWidth;
    const height = 500;

    // Clear previous
    d3.select(svgRef.current).selectAll('*').remove();

    const svg = d3
      .select(svgRef.current)
      .attr('width', width)
      .attr('height', height);

    // Create force simulation
    const nodes = topology.nodes.map((n) => ({ ...n }));
    const links = topology.edges.map((e) => ({
      source: e.source,
      target: e.target,
    }));

    const simulation = d3
      .forceSimulation(nodes)
      .force('link', d3.forceLink(links).id((d: any) => d.id).distance(120))
      .force('charge', d3.forceManyBody().strength(-400))
      .force('center', d3.forceCenter(width / 2, height / 2))
      .force('collision', d3.forceCollide().radius(50));

    // Draw links
    const link = svg
      .append('g')
      .selectAll('line')
      .data(links)
      .join('line')
      .attr('stroke', '#1f2937')
      .attr('stroke-width', 2);

    // Draw nodes
    const node = svg
      .append('g')
      .selectAll('g')
      .data(nodes)
      .join('g')
      .call(
        d3
          .drag<SVGGElement, any>()
          .on('start', (event, d) => {
            if (!event.active) simulation.alphaTarget(0.3).restart();
            d.fx = d.x;
            d.fy = d.y;
          })
          .on('drag', (event, d) => {
            d.fx = event.x;
            d.fy = event.y;
          })
          .on('end', (event, d) => {
            if (!event.active) simulation.alphaTarget(0);
            d.fx = null;
            d.fy = null;
          })
      )
      .on('click', (event, d) => {
        setSelectedNode(d);
      });

    // Node circles
    node
      .append('circle')
      .attr('r', 25)
      .attr('fill', (d: any) => {
        if (d.failureMode === 'HEALTHY') return '#10b981';
        if (d.healthScore > 0.7) return '#f59e0b';
        return '#ef4444';
      })
      .attr('stroke', '#1f2937')
      .attr('stroke-width', 2);

    // Node labels
    node
      .append('text')
      .text((d: any) => d.name.split('-')[0])
      .attr('text-anchor', 'middle')
      .attr('dy', '0.35em')
      .attr('fill', '#ffffff')
      .attr('font-size', '10px')
      .attr('pointer-events', 'none');

    // Update positions
    simulation.on('tick', () => {
      link
        .attr('x1', (d: any) => d.source.x)
        .attr('y1', (d: any) => d.source.y)
        .attr('x2', (d: any) => d.target.x)
        .attr('y2', (d: any) => d.target.y);

      node.attr('transform', (d: any) => `translate(${d.x},${d.y})`);
    });

    // Cleanup
    return () => {
      simulation.stop();
    };
  }, [topology]);

  const getFailureBadgeVariant = (failureMode: string) => {
    if (failureMode === 'HEALTHY') return 'success';
    if (failureMode.includes('CRASH') || failureMode.includes('DEADLOCK')) return 'danger';
    return 'warning';
  };

  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-sentinel-text">Service Topology</h1>
          <p className="text-sentinel-muted mt-1">Dependency graph and service health</p>
        </div>
        <Button onClick={() => fetchTopology()} variant="secondary" size="sm">
          Refresh
        </Button>
      </div>

      {isLoading || !topology ? (
        <div className="flex items-center justify-center h-96">
          <LoadingSpinner size="lg" />
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          {/* Topology Graph */}
          <div className="lg:col-span-3">
            <Card title="Service Dependency Graph" subtitle="Drag nodes to rearrange">
              <div ref={containerRef} className="bg-sentinel-dark rounded border border-sentinel-border">
                <svg ref={svgRef} style={{ width: '100%', height: '500px' }} />
              </div>
            </Card>
          </div>

          {/* Node Details */}
          <div>
            <Card title="Node Details">
              {selectedNode ? (
                <div className="space-y-3">
                  <div>
                    <p className="text-sm text-sentinel-muted">Service</p>
                    <p className="text-sentinel-text font-medium">{selectedNode.name}</p>
                  </div>
                  <div>
                    <p className="text-sm text-sentinel-muted">Status</p>
                    <Badge variant={getFailureBadgeVariant(selectedNode.failureMode)}>
                      {selectedNode.failureMode.replace(/_/g, ' ')}
                    </Badge>
                  </div>
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-sentinel-muted">CPU</span>
                      <span className="text-sentinel-text">{(selectedNode.cpu * 100).toFixed(1)}%</span>
                    </div>
                    <div className="w-full bg-sentinel-dark rounded-full h-2">
                      <div
                        className="bg-sentinel-blue h-2 rounded-full transition-all"
                        style={{ width: `${selectedNode.cpu * 100}%` }}
                      />
                    </div>

                    <div className="flex items-center justify-between">
                      <span className="text-sm text-sentinel-muted">Memory</span>
                      <span className="text-sentinel-text">{(selectedNode.memory * 100).toFixed(1)}%</span>
                    </div>
                    <div className="w-full bg-sentinel-dark rounded-full h-2">
                      <div
                        className="bg-sentinel-green h-2 rounded-full transition-all"
                        style={{ width: `${selectedNode.memory * 100}%` }}
                      />
                    </div>

                    <div className="flex items-center justify-between">
                      <span className="text-sm text-sentinel-muted">Error Rate</span>
                      <span className="text-sentinel-text">{(selectedNode.errorRate * 100).toFixed(2)}%</span>
                    </div>
                    <div className="w-full bg-sentinel-dark rounded-full h-2">
                      <div
                        className={`h-2 rounded-full transition-all ${
                          selectedNode.errorRate > 0.1 ? 'bg-sentinel-red' : 'bg-sentinel-yellow'
                        }`}
                        style={{ width: `${Math.min(selectedNode.errorRate * 100 * 10, 100)}%` }}
                      />
                    </div>
                  </div>
                  <div>
                    <p className="text-sm text-sentinel-muted">Health Score</p>
                    <p className="text-2xl font-bold text-sentinel-text">
                      {(selectedNode.healthScore * 100).toFixed(1)}%
                    </p>
                  </div>
                </div>
              ) : (
                <p className="text-sentinel-muted text-sm">Click a node to view details</p>
              )}
            </Card>

            {/* Service List */}
            <Card title="Services" className="mt-4">
              <div className="space-y-2 max-h-64 overflow-y-auto">
                {topology.nodes.map((node) => (
                  <button
                    key={node.id}
                    onClick={() => setSelectedNode(node)}
                    className={`w-full text-left p-2 rounded border transition-colors ${
                      selectedNode?.id === node.id
                        ? 'bg-sentinel-blue/20 border-sentinel-blue'
                        : 'bg-sentinel-dark border-sentinel-border hover:border-sentinel-muted'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-sentinel-text">{node.name}</span>
                      <Badge variant={getFailureBadgeVariant(node.failureMode)} size="sm">
                        {node.failureMode === 'HEALTHY' ? 'OK' : 'FAIL'}
                      </Badge>
                    </div>
                  </button>
                ))}
              </div>
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}
