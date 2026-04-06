import { NavLink } from 'react-router-dom';



interface NavItem {

  label: string;

  icon: string;

  to: string;

}



const NAV_ITEMS: NavItem[] = [

  { label: 'Dashboard',    icon: 'dashboard',       to: '/' },

  { label: 'Self-Healing', icon: 'auto_fix_high',   to: '/healing' },

  { label: 'Training',     icon: 'model_training',  to: '/training' },

  { label: 'Topology',     icon: 'hub',             to: '/topology' },

];



export default function Sidebar() {

  return (

    <aside className="fixed left-0 top-0 h-screen w-64 z-50 flex flex-col

                      bg-surface border-r border-outline-variant/15 shadow-sidebar">

      {/* Logo */}

      <div className="px-6 py-8 flex items-center gap-3">

        <div className="w-9 h-9 bg-primary rounded-xl flex items-center justify-center shrink-0">

          <span className="material-symbols-outlined icon-filled text-on-primary text-xl">shield</span>

        </div>

        <div>

          <span className="font-headline font-bold text-lg text-primary tracking-tighter uppercase leading-none">

            CloudSentinel

          </span>

          <p className="text-[10px] text-on-surface-variant uppercase tracking-[0.2em] mt-0.5">

            AI Operations

          </p>

        </div>

      </div>



      {/* Nav */}

      <nav className="flex-1 px-4 space-y-1">

        {NAV_ITEMS.map(({ label, icon, to }) => (

          <NavLink

            key={to}

            to={to}

            end={to === '/'}

            className={({ isActive }) =>

              `flex items-center gap-4 px-4 py-3 rounded-xl text-sm font-headline tracking-tight

               transition-all duration-200 group

               ${isActive

                 ? 'text-primary font-bold border-r-2 border-primary bg-surface-high/60'

                 : 'text-on-surface-variant opacity-70 hover:opacity-100 hover:bg-surface-high hover:text-on-surface'

               }`

            }

          >

            {({ isActive }) => (

              <>

                <span className={`material-symbols-outlined text-xl ${isActive ? 'icon-filled' : ''}`}>

                  {icon}

                </span>

                {label}

              </>

            )}

          </NavLink>

        ))}

      </nav>



      {/* System Status Pill */}

      <div className="mx-4 p-3 bg-surface-lowest rounded-xl border border-outline-variant/10 mb-4">

        <div className="flex items-center gap-2 mb-1">

          <div className="relative w-2 h-2">

            <span className="absolute inset-0 bg-primary rounded-full animate-ripple" />

            <span className="relative block w-2 h-2 bg-primary rounded-full" />

          </div>

          <span className="text-xs font-bold text-primary">System Healthy</span>

        </div>

        <p className="text-[10px] text-on-surface-variant">Uptime: 99.998%</p>

      </div>



      {/* User card */}

      <div className="px-4 pb-6">

        <div className="p-3 bg-surface-high rounded-xl border border-outline-variant/10 flex items-center gap-3">

          <div className="w-9 h-9 rounded-full bg-primary/20 flex items-center justify-center shrink-0">

            <span className="material-symbols-outlined text-primary text-sm">person</span>

          </div>

          <div className="overflow-hidden">

            <p className="text-xs font-bold text-on-surface truncate">Alex Chen</p>

            <p className="text-[10px] text-on-surface-variant uppercase tracking-wider truncate">Lead SRE</p>

          </div>

        </div>

      </div>

    </aside>

  );

}
