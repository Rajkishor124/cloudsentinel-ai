import { useEffect, useRef, useState, useCallback } from 'react';
import * as d3 from 'd3';
import { useSimulationStore } from '../stores/simulationStore';
import Layout from '../components/layout/Layout';
import { GhostButton, StatusBadge, EmptyState, Spinner } from '../components/ui/primitives';
import type { ServiceNode } from '../types/api';

// ─── Node color helpers ───────────────────────────────────────────────────────
const nodeColor = (n: ServiceNode) => {
  if (n.failureMode === 'HEALTHY') return '#3adfab';
  if (n.healthScore > 0.7)         return '#ffb95f';
  return '#ffb4ab';
};

const nodeSeverity = (n: ServiceNode): 'success' | 'warning' | 'danger' => {
  if (n.failureMode === 'HEALTHY') return 'success';
  if (n.healthScore > 0.7)         return 'warning';
  return 'danger';
};

export default function TopologyPage() {
  const { topology, fetchTopology, injectFailure, isLoading } = useSimulationStore();
  const svgRef       = useRef<SVGSVGElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const simRef       = useRef<d3.Simulation<any, any> | null>(null);
  const [selectedNode, setSelectedNode]     = useState<ServiceNode | null>(null);
  const [injectMenu, setInjectMenu]         = useState(false);
  const [layoutMode, setLayoutMode]         = useState<'force' | 'hierarchical'>('force');

  const buildGraph = useCallback(() => {
    if (!topology || !svgRef.current || !containerRef.current) return;

    const width  = containerRef.current.clientWidth;
    const height = containerRef.current.clientHeight || 520;
    const svg    = d3.select(svgRef.current).attr('width', width).attr('height', height);
    svg.selectAll('*').remove();

    // Defs: arrow markers per color
    const defs = svg.append('defs');
    [
      { id: 'arrow-green', fill: '#3adfab' },
      { id: 'arrow-amber', fill: '#ffb95f' },
      { id: 'arrow-red',   fill: '#ffb4ab' },
    ].forEach(({ id, fill }) =>
      defs.append('marker').attr('id', id)
          .attr('markerWidth', 6).attr('markerHeight', 6)
          .attr('refX', 32).attr('refY', 3)
          .attr('orient', 'auto')
          .attr('viewBox', '0 0 6 6')
          .append('path').attr('d', 'M0,0 L6,3 L0,6 Z').attr('fill', fill)
    );

    const nodes: any[] = topology.nodes.map(n => ({ ...n }));
    const links: any[] = topology.edges.map(e => ({ source: e.source, target: e.target }));

    // Force simulation
    simRef.current?.stop();
    const sim = d3.forceSimulation(nodes)
      .force('link',      d3.forceLink(links).id((d: any) => d.id).distance(140))
      .force('charge',    d3.forceManyBody().strength(-500))
      .force('center',    d3.forceCenter(width / 2, height / 2))
      .force('collision', d3.forceCollide().radius(55));

    if (layoutMode === 'hierarchical') {
      sim.force('y', d3.forceY().strength(0.15));
      sim.force('x', d3.forceX(width / 2).strength(0.05));
    }

    simRef.current = sim;

    // Links
    const link = svg.append('g').selectAll('line').data(links).join('line')
      .attr('stroke', (d: any) => {
        const src = topology.nodes.find(n => n.id === (d.source.id ?? d.source));
        return src ? nodeColor(src) : '#424754';
      })
      .attr('stroke-width', 1.5)
      .attr('stroke-dasharray', (d: any) => {
        const src = topology.nodes.find(n => n.id === (d.source.id ?? d.source));
        return src && src.healthScore < 0.5 ? '6 3' : 'none';
      })
      .attr('marker-end', (d: any) => {
        const src = topology.nodes.find(n => n.id === (d.source.id ?? d.source));
        if (!src) return 'url(#arrow-green)';
        if (src.failureMode === 'HEALTHY') return 'url(#arrow-green)';
        if (src.healthScore > 0.7)         return 'url(#arrow-amber)';
        return 'url(#arrow-red)';
      })
      .attr('opacity', 0.7);

    // Node groups
    const node = svg.append('g').selectAll<SVGGElement, any>('g')
      .data(nodes).join('g')
      .style('cursor', 'pointer')
      .call(
        d3.drag<SVGGElement, any>()
          .on('start', (ev, d) => { if (!ev.active) sim.alphaTarget(0.3).restart(); d.fx = d.x; d.fy = d.y; })
          .on('drag',  (ev, d) => { d.fx = ev.x; d.fy = ev.y; })
          .on('end',   (ev, d) => { if (!ev.active) sim.alphaTarget(0); d.fx = null; d.fy = null; })
      )
      .on('click', (_ev, d) => {
        setSelectedNode(topology.nodes.find(n => n.id === d.id) ?? null);
      });

    // Pulse ring (for failed/degraded)
    node.filter((d: any) => d.failureMode !== 'HEALTHY')
        .append('circle')
        .attr('r', 32).attr('fill', 'none')
        .attr('stroke', (d: any) => nodeColor(d)).attr('stroke-width', 1.5)
        .attr('opacity', 0.4)
        .style('animation', 'ripple 2s infinite ease-out');

    // Main circle
    node.append('circle')
        .attr('r', 26)
        .attr('fill', (d: any) => `${nodeColor(d)}22`)
        .attr('stroke', (d: any) => nodeColor(d))
        .attr('stroke-width', 2);

    // Icon letter
    node.append('text')
        .text((d: any) => d.name.charAt(0).toUpperCase())
        .attr('text-anchor', 'middle').attr('dy', '0.35em')
        .attr('fill', (d: any) => nodeColor(d))
        .attr('font-size', 14).attr('font-weight', 700)
        .attr('pointer-events', 'none');

    // Label below
    node.append('text')
        .text((d: any) => d.name.split('-')[0])
        .attr('text-anchor', 'middle').attr('dy', 44)
        .attr('fill', '#c2c6d6').attr('font-size', 10)
        .attr('pointer-events', 'none');

    // Health % badge above
    node.append('text')
        .text((d: any) => `${(d.healthScore * 100).toFixed(0)}%`)
        .attr('text-anchor', 'middle').attr('dy', -38)
        .attr('fill', (d: any) => nodeColor(d)).attr('font-size', 9).attr('font-weight', 700)
        .attr('pointer-events', 'none');

    sim.on('tick', () => {
      link
        .attr('x1', (d: any) => d.source.x).attr('y1', (d: any) => d.source.y)
        .attr('x2', (d: any) => d.target.x).attr('y2', (d: any) => d.target.y);
      node.attr('transform', (d: any) => `translate(${d.x},${d.y})`);
    });

    return () => sim.stop();
  }, [topology, layoutMode]);

  useEffect(() => { fetchTopology(); }, []);
  useEffect(() => { const cleanup = buildGraph(); return () => { cleanup?.(); }; }, [buildGraph]);

  const handleInject = async (mode: string) => {
    setInjectMenu(false);
    await injectFailure(mode);
    await fetchTopology();
  };

  const headerActions = (
    <>
      <GhostButton onClick={fetchTopology} icon="refresh">Refresh</GhostButton>
      <div className="relative">
        <button
          onClick={() => setInjectMenu(p => !p)}
          className="flex items-center gap-2 px-4 py-2 bg-tertiary/10 text-tertiary
                     border border-tertiary/20 rounded-xl text-xs font-bold
                     hover:bg-tertiary/20 transition-colors"
        >
          Inject Failure
          <span className="material-symbols-outlined text-sm">expand_more</span>
        </button>
        {injectMenu && (
          <div className="absolute right-0 top-full mt-2 w-52 bg-surface-highest
                          border border-outline-variant/30 rounded-xl shadow-2xl z-50 p-1">
            {[
              { label: 'CPU Spike',     mode: 'CPU_SPIKE' },
              { label: 'Memory Leak',   mode: 'MEMORY_LEAK' },
              { label: 'Service Crash', mode: 'SERVICE_CRASH' },
              { label: 'Network Partition', mode: 'NETWORK_PARTITION' },
            ].map(({ label, mode }) => (
              <button key={mode} onClick={() => handleInject(mode)}
                      className="w-full text-left px-4 py-2.5 text-sm text-on-surface
                                  hover:bg-surface-container rounded-lg transition-colors">
                {label}
              </button>
            ))}
          </div>
        )}
      </div>
    </>
  );

  return (
    <Layout title="Service Topology" subtitle="Dependency graph and service health" actions={headerActions}>
      {isLoading && !topology ? (
        <div className="flex items-center justify-center h-[600px]">
          <Spinner size="lg" />
        </div>
      ) : (
        <div className="flex gap-6 h-[calc(100vh-10rem)]">

          {/* ── Graph Canvas (75%) ──────────────────────────────────────── */}
          <section className="flex-1 relative bg-[#0A0E14] dot-grid rounded-2xl
                               border border-outline-variant/10 overflow-hidden">
            <div ref={containerRef} className="w-full h-full">
              <svg ref={svgRef} className="w-full h-full" />
            </div>

            {/* Zoom + layout controls */}
            <div className="absolute top-4 left-4 flex flex-col gap-2">
              <div className="bg-surface-high/90 backdrop-blur-md rounded-xl border border-outline-variant/20 p-1 flex flex-col">
                <button onClick={() => { const z = svgRef.current; if (z) z.style.transform = 'scale(1.2)'; }}
                        className="p-2 hover:bg-surface-highest rounded-lg transition-colors text-on-surface">
                  <span className="material-symbols-outlined">add</span>
                </button>
                <div className="h-px bg-outline-variant/30 mx-2" />
                <button onClick={() => { const z = svgRef.current; if (z) z.style.transform = 'scale(0.8)'; }}
                        className="p-2 hover:bg-surface-highest rounded-lg transition-colors text-on-surface">
                  <span className="material-symbols-outlined">remove</span>
                </button>
              </div>
              <button className="bg-surface-high/90 backdrop-blur-md rounded-xl border border-outline-variant/20 p-3
                                  text-on-surface hover:bg-surface-highest transition-colors">
                <span className="material-symbols-outlined">fullscreen</span>
              </button>
              <div className="bg-surface-high/90 backdrop-blur-md rounded-xl border border-outline-variant/20 p-1 flex gap-1">
                {(['force', 'hierarchical'] as const).map(m => (
                  <button key={m}
                          onClick={() => setLayoutMode(m)}
                          className={`px-3 py-1 rounded-lg text-xs font-bold capitalize transition-colors
                                       ${layoutMode === m
                                         ? 'bg-primary-container text-on-primary-container'
                                         : 'text-on-surface-variant hover:bg-surface-highest'}`}>
                    {m}
                  </button>
                ))}
              </div>
            </div>

            {/* Legend overlay */}
            <div className="absolute bottom-4 left-4 bg-surface-high/90 backdrop-blur-md
                             rounded-xl border border-outline-variant/20 p-3">
              <div className="grid grid-cols-2 gap-x-4 gap-y-1.5">
                {[
                  { color: '#3adfab', label: 'Healthy' },
                  { color: '#ffb95f', label: 'Degraded' },
                  { color: '#ffb4ab', label: 'Failed' },
                  { color: '#424754', label: 'Unknown' },
                ].map(({ color, label }) => (
                  <div key={label} className="flex items-center gap-1.5">
                    <div className="w-2 h-2 rounded-full" style={{ backgroundColor: color }} />
                    <span className="text-[10px] text-on-surface-variant">{label}</span>
                  </div>
                ))}
              </div>
            </div>
          </section>

          {/* ── Right Sidebar (25%) ─────────────────────────────────────── */}
          <aside className="w-72 flex flex-col gap-4 overflow-y-auto shrink-0">

            {/* Node Details */}
            <div className="bg-surface-container rounded-2xl border border-outline-variant/10 overflow-hidden">
              {selectedNode ? (
                <>
                  <div className={`p-4 border-b flex items-center justify-between
                                   ${selectedNode.failureMode === 'HEALTHY'
                                     ? 'bg-primary/5 border-primary/20'
                                     : selectedNode.healthScore > 0.7
                                       ? 'bg-tertiary/5 border-tertiary/20'
                                       : 'bg-error/5 border-error/20'}`}>
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-xl flex items-center justify-center"
                           style={{ backgroundColor: `${nodeColor(selectedNode)}22` }}>
                        <span className="text-sm font-bold" style={{ color: nodeColor(selectedNode) }}>
                          {selectedNode.name.charAt(0).toUpperCase()}
                        </span>
                      </div>
                      <div>
                        <p className="text-sm font-bold leading-none">{selectedNode.name}</p>
                        <p className="text-[10px] text-on-surface-variant mt-0.5">
                          {selectedNode.failureMode.replace(/_/g, ' ')}
                        </p>
                      </div>
                    </div>
                    <StatusBadge variant={nodeSeverity(selectedNode)}>
                      {selectedNode.failureMode === 'HEALTHY' ? 'OK' : 'FAIL'}
                    </StatusBadge>
                  </div>

                  <div className="p-5 space-y-4">
                    {[
                      { label: 'CPU Usage',    value: selectedNode.cpu,       color: 'bg-secondary' },
                      { label: 'Memory Usage', value: selectedNode.memory,    color: 'bg-tertiary' },
                      { label: 'Error Rate',   value: selectedNode.errorRate, color: 'bg-error' },
                    ].map(({ label, value, color }) => (
                      <div key={label} className="space-y-1.5">
                        <div className="flex justify-between text-xs">
                          <span className="text-on-surface-variant">{label}</span>
                          <span className={`font-bold ${value > 0.8 ? 'text-error' : 'text-on-surface'}`}>
                            {(value * 100).toFixed(1)}%
                          </span>
                        </div>
                        <div className="h-1.5 w-full bg-surface-highest rounded-full overflow-hidden">
                          <div className={`h-full ${color} rounded-full transition-all duration-500`}
                               style={{ width: `${Math.min(value * 100, 100)}%` }} />
                        </div>
                      </div>
                    ))}

                    <div className="grid grid-cols-2 gap-3 pt-2 border-t border-outline-variant/10">
                      <div>
                        <p className="text-[10px] uppercase text-on-surface-variant font-bold tracking-wider mb-1">
                          Latency
                        </p>
                        <p className="text-base font-headline font-bold">
                          {(selectedNode.latency * 1000).toFixed(0)}ms
                        </p>
                      </div>
                      <div>
                        <p className="text-[10px] uppercase text-on-surface-variant font-bold tracking-wider mb-1">
                          Health
                        </p>
                        <p className={`text-base font-headline font-bold`}
                           style={{ color: nodeColor(selectedNode) }}>
                          {(selectedNode.healthScore * 100).toFixed(1)}%
                        </p>
                      </div>
                    </div>

                    <button className="w-full bg-surface-highest hover:bg-surface-bright text-on-surface
                                        text-xs font-bold py-2.5 rounded-xl transition-colors">
                      View Detailed Logs
                    </button>
                  </div>
                </>
              ) : (
                <div className="p-6">
                  <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-4">
                    Node Details
                  </p>
                  <EmptyState icon="ads_click" title="Click a node" sub="Select a service to view details" />
                </div>
              )}
            </div>

            {/* Services List */}
            <div className="bg-surface-container rounded-2xl border border-outline-variant/10 p-4">
              <p className="text-xs font-bold uppercase tracking-widest text-on-surface-variant mb-3">
                Services ({topology?.nodes.length ?? 0})
              </p>
              <div className="space-y-1.5 max-h-64 overflow-y-auto">
                {topology?.nodes.map(node => (
                  <button
                    key={node.id}
                    onClick={() => setSelectedNode(node)}
                    className={`w-full text-left flex items-center justify-between p-3
                                 rounded-xl border transition-all
                                 ${selectedNode?.id === node.id
                                   ? 'bg-primary/10 border-primary/30'
                                   : 'bg-surface-low border-outline-variant/10 hover:border-outline-variant/30'}`}
                  >
                    <div className="flex items-center gap-2.5">
                      <div className="w-2 h-2 rounded-full"
                           style={{ backgroundColor: nodeColor(node) }} />
                      <span className={`text-xs font-medium
                                         ${selectedNode?.id === node.id ? 'text-on-surface font-bold' : 'text-on-surface-variant'}`}>
                        {node.name}
                      </span>
                    </div>
                    <span className="text-[10px] font-bold"
                          style={{ color: nodeColor(node) }}>
                      {(node.healthScore * 100).toFixed(0)}%
                    </span>
                  </button>
                ))}
              </div>
            </div>

          </aside>
        </div>
      )}
    </Layout>
  );
}
