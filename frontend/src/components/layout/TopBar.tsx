import type { ReactNode } from 'react';



interface TopBarProps {

  title: string;

  subtitle: string;

  actions?: ReactNode;

}



export default function TopBar({ title, subtitle, actions }: TopBarProps) {

  return (

    <header

      className="fixed top-0 right-0 z-40 flex items-center justify-between h-20 px-8

                 bg-surface/80 backdrop-blur-xl border-b border-outline-variant/15"

      style={{ left: '16rem', width: 'calc(100% - 16rem)' }}

    >

      <div>

        <h1 className="text-xl font-headline font-bold text-on-surface leading-none">{title}</h1>

        <p className="text-xs text-on-surface-variant mt-1">{subtitle}</p>

      </div>



      <div className="flex items-center gap-3">

        {actions}

        <div className="h-6 w-px bg-outline-variant/30 mx-1" />

        <button className="w-9 h-9 flex items-center justify-center rounded-xl

                           text-on-surface-variant hover:text-primary hover:bg-surface-high

                           transition-colors relative">

          <span className="material-symbols-outlined text-xl">notifications</span>

          <span className="absolute top-2 right-2 w-1.5 h-1.5 bg-primary rounded-full" />

        </button>

        <button className="w-9 h-9 flex items-center justify-center rounded-xl

                           text-on-surface-variant hover:text-primary hover:bg-surface-high

                           transition-colors">

          <span className="material-symbols-outlined text-xl">settings</span>

        </button>

      </div>

    </header>

  );

}
