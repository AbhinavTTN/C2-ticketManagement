'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { fetchTicket, updateTicket, ApiError } from '@/lib/api';
import { Ticket, TicketPriority } from '@/types/ticket';
import { ArrowLeft, AlertCircle, Save } from 'lucide-react';

export default function EditTicketPage() {
  const params = useParams();
  const router = useRouter();
  const id = params?.id as string;

  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    priority: 'HIGH' as TicketPriority,
    assignee: '',
    category: '',
  });

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [topError, setTopError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const loadTicket = useCallback(async () => {
    if (!id) return;
    setIsLoading(true);
    setTopError(null);
    try {
      const data = await fetchTicket(id);
      setTicket(data);
      setFormData({
        title: data.title,
        description: data.description,
        priority: data.priority,
        assignee: data.assignee || '',
        category: data.category,
      });
    } catch (err: unknown) {
      if (err instanceof ApiError && err.status === 404) {
        setTopError(err.detail);
        setTimeout(() => router.push('/tickets'), 3000);
      } else if (err instanceof ApiError) {
        setTopError(err.detail);
      } else {
        setTopError('Failed to load ticket.');
      }
    } finally {
      setIsLoading(false);
    }
  }, [id, router]);

  useEffect(() => {
    loadTicket();
  }, [loadTicket]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!ticket) return;
    setIsSubmitting(true);
    setFieldErrors({});
    setTopError(null);

    try {
      await updateTicket(ticket.id, {
        title: formData.title,
        description: formData.description,
        priority: formData.priority,
        assignee: formData.assignee.trim() === '' ? '' : formData.assignee,
        category: formData.category,
      });

      router.push(`/tickets/${ticket.id}`);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setTopError(err.detail);
        setFieldErrors(err.fields);
      } else {
        setTopError('An unexpected network error occurred while updating the ticket.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex h-96 flex-col items-center justify-center text-slate-400 space-y-3">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-indigo-600 border-t-transparent" />
        <span className="text-sm font-medium text-slate-500">Loading ticket for editing...</span>
      </div>
    );
  }

  if (!ticket && topError) {
    return (
      <div className="mx-auto max-w-xl px-4 py-16 text-center space-y-4">
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-100 text-red-600">
          <AlertCircle className="h-6 w-6" />
        </div>
        <h1 className="text-xl font-bold text-slate-900">Ticket Not Found</h1>
        <p className="text-sm text-slate-600">{topError}</p>
        <p className="text-xs text-slate-400">Redirecting to tickets list...</p>
        <Link href="/tickets" className="text-xs font-semibold text-indigo-600 hover:underline">
          Return to list now
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      <div>
        <Link
          href={`/tickets/${id}`}
          className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-slate-800 transition"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          <span>Back to Ticket #{id}</span>
        </Link>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 shadow-sm space-y-6">
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <div>
            <h1 className="text-xl font-bold tracking-tight text-slate-900">
              Edit Ticket #{ticket?.id}
            </h1>
            <p className="text-xs text-slate-500 mt-0.5">
              Updates to title, description, or category automatically trigger re-embedding.
            </p>
          </div>
          <div className="text-right">
            <span className="text-xs text-slate-400 block">Status (read-only)</span>
            <span className="text-xs font-semibold text-slate-700">{ticket?.status}</span>
          </div>
        </div>

        {topError && (
          <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800 flex items-start gap-3">
            <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0 mt-0.5" />
            <div className="space-y-1">
              <h3 className="font-semibold">Unable to save changes</h3>
              <p className="text-xs text-red-700">{topError}</p>
            </div>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Title */}
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
              Title <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              maxLength={200}
              value={formData.title}
              onChange={(e) => setFormData({ ...formData, title: e.target.value })}
              className={`w-full rounded-xl border px-3.5 py-2.5 text-sm text-slate-900 ${
                fieldErrors.title
                  ? 'border-red-400 bg-red-50/20'
                  : 'border-slate-200 bg-slate-50/50 focus:bg-white'
              }`}
            />
            {fieldErrors.title && (
              <p className="mt-1.5 text-xs text-red-600 font-medium">{fieldErrors.title}</p>
            )}
          </div>

          {/* Description */}
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
              Description <span className="text-red-500">*</span>
            </label>
            <textarea
              rows={5}
              maxLength={10000}
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              className={`w-full rounded-xl border px-3.5 py-2.5 text-sm text-slate-900 ${
                fieldErrors.description
                  ? 'border-red-400 bg-red-50/20'
                  : 'border-slate-200 bg-slate-50/50 focus:bg-white'
              }`}
            />
            {fieldErrors.description && (
              <p className="mt-1.5 text-xs text-red-600 font-medium">{fieldErrors.description}</p>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Priority */}
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
                Priority
              </label>
              <select
                value={formData.priority}
                onChange={(e) =>
                  setFormData({ ...formData, priority: e.target.value as TicketPriority })
                }
                className="w-full rounded-xl border border-slate-200 bg-slate-50/50 px-3.5 py-2.5 text-sm text-slate-900"
              >
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
                <option value="URGENT">URGENT</option>
              </select>
            </div>

            {/* Category */}
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
                Category <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                maxLength={64}
                value={formData.category}
                onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                className={`w-full rounded-xl border px-3.5 py-2.5 text-sm text-slate-900 ${
                  fieldErrors.category
                    ? 'border-red-400 bg-red-50/20'
                    : 'border-slate-200 bg-slate-50/50 focus:bg-white'
                }`}
              />
              {fieldErrors.category && (
                <p className="mt-1.5 text-xs text-red-600 font-medium">{fieldErrors.category}</p>
              )}
            </div>
          </div>

          {/* Assignee */}
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
              Assignee <span className="text-slate-400 font-normal">(leave blank to clear)</span>
            </label>
            <input
              type="text"
              maxLength={120}
              value={formData.assignee}
              onChange={(e) => setFormData({ ...formData, assignee: e.target.value })}
              placeholder="Leave empty to clear assignee"
              className="w-full rounded-xl border border-slate-200 bg-slate-50/50 px-3.5 py-2.5 text-sm text-slate-900"
            />
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-100">
            <Link
              href={`/tickets/${id}`}
              className="rounded-xl border border-slate-200 bg-white px-5 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50 transition"
            >
              Cancel
            </Link>
            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex items-center gap-2 rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-medium text-white shadow-sm hover:bg-slate-800 disabled:opacity-50 transition"
            >
              {isSubmitting ? (
                <>
                  <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  <span>Saving...</span>
                </>
              ) : (
                <>
                  <Save className="h-4 w-4" />
                  <span>Update Ticket</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
