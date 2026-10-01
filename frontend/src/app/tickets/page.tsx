'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { fetchTickets, ApiError } from '@/lib/api';
import { TicketSummary, TicketStatus, TicketPriority } from '@/types/ticket';
import { useAiChat } from '@/components/AiChatContext';
import {
  Search,
  Plus,
  Sparkles,
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  Filter,
  X,
  Inbox,
  Clock,
  User,
  Tag,
} from 'lucide-react';

const STATUSES: { label: string; value: TicketStatus | '' }[] = [
  { label: 'All', value: '' },
  { label: 'Open', value: 'OPEN' },
  { label: 'In Progress', value: 'IN_PROGRESS' },
  { label: 'Resolved', value: 'RESOLVED' },
  { label: 'Closed', value: 'CLOSED' },
  { label: 'Cancelled', value: 'CANCELLED' },
];

export default function TicketListPage() {
  const router = useRouter();
  const { openPanel } = useAiChat();

  const [tickets, setTickets] = useState<TicketSummary[]>([]);
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [status, setStatus] = useState<TicketStatus | ''>('');
  const [searchInput, setSearchInput] = useState('');
  const [activeQuery, setActiveQuery] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<{ title: string; detail: string } | null>(null);

  const loadTickets = useCallback(
    async (pageToLoad: number, statusFilter: TicketStatus | '', queryText: string) => {
      setIsLoading(true);
      setError(null);
      try {
        const response = await fetchTickets({
          status: statusFilter,
          q: queryText,
          page: pageToLoad,
          size,
        });
        setTickets(response.content);
        setPage(response.page);
        setTotalElements(response.totalElements);
        setTotalPages(response.totalPages);
      } catch (err: unknown) {
        if (err instanceof ApiError) {
          setError({ title: err.title, detail: err.detail });
        } else {
          setError({
            title: 'Failed to load tickets',
            detail: 'An unexpected network error occurred while connecting to the backend.',
          });
        }
      } finally {
        setIsLoading(false);
      }
    },
    [size]
  );

  useEffect(() => {
    loadTickets(page, status, activeQuery);
  }, [loadTickets, page, status, activeQuery]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setActiveQuery(searchInput.trim());
  };

  const handleStatusChange = (newStatus: TicketStatus | '') => {
    setStatus(newStatus);
    setPage(0);
  };

  const handleClearFilters = () => {
    setStatus('');
    setSearchInput('');
    setActiveQuery('');
    setPage(0);
  };

  const getStatusBadge = (s: TicketStatus) => {
    switch (s) {
      case 'OPEN':
        return 'bg-blue-50 text-blue-700 border-blue-200';
      case 'IN_PROGRESS':
        return 'bg-amber-50 text-amber-700 border-amber-200';
      case 'RESOLVED':
        return 'bg-green-50 text-green-700 border-green-200';
      case 'CLOSED':
        return 'bg-slate-100 text-slate-700 border-slate-300';
      case 'CANCELLED':
        return 'bg-rose-50 text-rose-700 border-rose-200';
      default:
        return 'bg-gray-50 text-gray-700 border-gray-200';
    }
  };

  const getPriorityBadge = (p: TicketPriority) => {
    switch (p) {
      case 'URGENT':
        return 'text-red-700 bg-red-50 border-red-200 font-semibold';
      case 'HIGH':
        return 'text-orange-700 bg-orange-50 border-orange-200 font-medium';
      case 'MEDIUM':
        return 'text-blue-700 bg-blue-50 border-blue-200 font-medium';
      case 'LOW':
        return 'text-slate-600 bg-slate-50 border-slate-200 font-normal';
      default:
        return 'text-gray-600 bg-gray-50 border-gray-200';
    }
  };

  const formatDate = (isoString: string) => {
    try {
      const d = new Date(isoString);
      return d.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  const hasActiveFilters = status !== '' || activeQuery !== '';

  return (
    <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Top Banner / Actions */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Support Tickets</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Manage issues, track resolutions, and query knowledge history.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => openPanel()}
            className="inline-flex items-center gap-2 rounded-xl border border-indigo-200 bg-indigo-50/50 px-4 py-2 text-sm font-medium text-indigo-700 hover:bg-indigo-100 hover:border-indigo-300 transition"
          >
            <Sparkles className="h-4 w-4 text-indigo-600" />
            <span>Ask AI</span>
          </button>
          <Link
            href="/tickets/new"
            className="inline-flex items-center gap-2 rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-slate-800 transition"
          >
            <Plus className="h-4 w-4" />
            <span>New Ticket</span>
          </Link>
        </div>
      </div>

      {/* Error Banner */}
      {error && (
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800 shadow-sm flex items-start gap-3">
          <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0 mt-0.5" />
          <div className="space-y-1">
            <h3 className="font-semibold">{error.title}</h3>
            <p className="text-xs text-red-700">{error.detail}</p>
          </div>
        </div>
      )}

      {/* Search and Filters */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        {/* Search */}
        <form onSubmit={handleSearchSubmit} className="relative flex-1 max-w-md">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search title, description, or comment author/body..."
            className="w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-10 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
          />
          {searchInput && (
            <button
              type="button"
              onClick={() => {
                setSearchInput('');
                setActiveQuery('');
                setPage(0);
              }}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              title="Clear search"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </form>

        {/* Status Filters */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 md:pb-0">
          <div className="flex items-center gap-1 text-xs text-slate-400 mr-1 font-medium">
            <Filter className="h-3.5 w-3.5" />
            <span>Status:</span>
          </div>
          {STATUSES.map((item) => (
            <button
              key={item.label}
              type="button"
              onClick={() => handleStatusChange(item.value)}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium whitespace-nowrap transition ${
                status === item.value
                  ? 'bg-slate-900 text-white shadow-sm'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200 hover:text-slate-900'
              }`}
            >
              {item.label}
            </button>
          ))}
          {hasActiveFilters && (
            <button
              type="button"
              onClick={handleClearFilters}
              className="text-xs text-indigo-600 hover:text-indigo-800 ml-2 whitespace-nowrap font-medium"
            >
              Clear all
            </button>
          )}
        </div>
      </div>

      {/* Ticket List Table */}
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        {isLoading ? (
          <div className="flex h-64 flex-col items-center justify-center text-slate-400 space-y-3">
            <div className="h-7 w-7 animate-spin rounded-full border-2 border-indigo-600 border-t-transparent" />
            <span className="text-xs font-medium text-slate-500">Loading tickets...</span>
          </div>
        ) : tickets.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 px-4 text-center">
            <div className="rounded-full bg-slate-100 p-3.5 text-slate-400 mb-3">
              <Inbox className="h-8 w-8" />
            </div>
            <h3 className="text-base font-semibold text-slate-900">No tickets match</h3>
            <p className="text-xs text-slate-500 max-w-sm mt-1 mb-6">
              {hasActiveFilters
                ? 'No tickets match your current status filter and search query. Try clearing filters or create a new ticket.'
                : 'There are currently no tickets in the database. Create the first ticket to begin tracking.'}
            </p>
            <div className="flex items-center gap-3">
              {hasActiveFilters && (
                <button
                  type="button"
                  onClick={handleClearFilters}
                  className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50"
                >
                  Clear Filters
                </button>
              )}
              <Link
                href="/tickets/new"
                className="rounded-xl bg-indigo-600 px-4 py-2 text-xs font-medium text-white hover:bg-indigo-700"
              >
                Create Ticket
              </Link>
            </div>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold text-slate-600 uppercase tracking-wider">
                <tr>
                  <th scope="col" className="px-6 py-3.5">
                    Ticket
                  </th>
                  <th scope="col" className="px-6 py-3.5">
                    Status
                  </th>
                  <th scope="col" className="px-6 py-3.5">
                    Priority
                  </th>
                  <th scope="col" className="px-6 py-3.5">
                    Assignee
                  </th>
                  <th scope="col" className="px-6 py-3.5">
                    Category
                  </th>
                  <th scope="col" className="px-6 py-3.5">
                    Last Updated
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {tickets.map((ticket) => (
                  <tr
                    key={ticket.id}
                    onClick={() => router.push(`/tickets/${ticket.id}`)}
                    className="group cursor-pointer hover:bg-slate-50/80 transition"
                  >
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2.5">
                        <span className="font-mono text-xs font-semibold text-slate-400 group-hover:text-indigo-600">
                          #{ticket.id}
                        </span>
                        <span className="font-medium text-slate-900 group-hover:text-indigo-600 line-clamp-1">
                          {ticket.title}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium ${getStatusBadge(
                          ticket.status
                        )}`}
                      >
                        {ticket.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs ${getPriorityBadge(
                          ticket.priority
                        )}`}
                      >
                        {ticket.priority}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-xs text-slate-600">
                      <div className="flex items-center gap-1.5">
                        <User className="h-3.5 w-3.5 text-slate-400" />
                        <span>{ticket.assignee || <span className="italic text-slate-400">Unassigned</span>}</span>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-xs text-slate-600">
                      <div className="flex items-center gap-1.5">
                        <Tag className="h-3.5 w-3.5 text-slate-400" />
                        <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[11px] text-slate-700">
                          {ticket.category}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-xs text-slate-500">
                      <div className="flex items-center gap-1.5">
                        <Clock className="h-3.5 w-3.5 text-slate-400" />
                        <span>{formatDate(ticket.updatedAt)}</span>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Footer */}
        <div className="flex items-center justify-between border-t border-slate-200 bg-slate-50 px-6 py-3.5 text-xs text-slate-600">
          <div>
            Showing page <span className="font-semibold text-slate-900">{totalPages === 0 ? 0 : page + 1}</span> of{' '}
            <span className="font-semibold text-slate-900">{totalPages}</span> ({totalElements} total tickets)
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={page === 0 || isLoading}
              onClick={() => setPage((prev) => Math.max(0, prev - 1))}
              className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
              <span>Previous</span>
            </button>
            <button
              type="button"
              disabled={page + 1 >= totalPages || totalPages === 0 || isLoading}
              onClick={() => setPage((prev) => prev + 1)}
              className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition"
            >
              <span>Next</span>
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
