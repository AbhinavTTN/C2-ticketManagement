'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAiChat } from './AiChatContext';
import { Ticket, PlusCircle, Sparkles } from 'lucide-react';

export default function Navbar() {
  const pathname = usePathname();
  const { openPanel } = useAiChat();

  const isTicketsActive = pathname === '/tickets' || pathname.startsWith('/tickets/');
  const isNewActive = pathname === '/tickets/new';

  return (
    <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8 h-16">
        <div className="flex items-center gap-8">
          <Link href="/tickets" className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-slate-900 text-white font-bold text-lg shadow-sm">
              <Ticket className="h-5 w-5 text-indigo-400" />
            </div>
            <div className="flex flex-col">
              <span className="font-bold text-slate-900 leading-tight">TicketDesk</span>
              <span className="text-[10px] text-slate-500 font-medium">Enterprise Support</span>
            </div>
          </Link>

          <nav className="flex items-center gap-1">
            <Link
              href="/tickets"
              className={`rounded-lg px-3 py-1.5 text-sm font-medium transition ${
                isTicketsActive && !isNewActive
                  ? 'bg-slate-100 text-slate-900'
                  : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
              }`}
            >
              All Tickets
            </Link>
            <Link
              href="/tickets/new"
              className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition ${
                isNewActive
                  ? 'bg-indigo-50 text-indigo-700'
                  : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
              }`}
            >
              <PlusCircle className="h-4 w-4" />
              <span>Create Ticket</span>
            </Link>
          </nav>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => openPanel()}
            className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-3.5 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 transition"
          >
            <Sparkles className="h-4 w-4" />
            <span>Ask AI</span>
          </button>
        </div>
      </div>
    </header>
  );
}
