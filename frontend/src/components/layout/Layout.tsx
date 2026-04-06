import type { ReactNode } from 'react';
import Sidebar from './Sidebar';
import TopBar from './TopBar';

interface LayoutProps {
  title: string;
  subtitle: string;
  actions?: ReactNode;
  children: ReactNode;
}

export default function Layout({ title, subtitle, actions, children }: LayoutProps) {
  return (
    <div className="min-h-screen bg-background">
      {/* Ambient glow decorations */}
      <div className="fixed inset-0 pointer-events-none -z-10 overflow-hidden">
        <div className="absolute top-[-10%] right-[-10%] w-[40%] h-[40%]
                        bg-primary/5 rounded-full blur-[120px]" />
        <div className="absolute bottom-[-10%] left-[-10%] w-[30%] h-[30%]
                        bg-secondary/5 rounded-full blur-[120px]" />
      </div>

      <Sidebar />
      <TopBar title={title} subtitle={subtitle} actions={actions} />

      <main className="ml-64 pt-24 p-8 min-h-screen">
        {children}
      </main>
    </div>
  );
}
