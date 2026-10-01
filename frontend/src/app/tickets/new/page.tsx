'use client';

import React, { useState, useRef } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { createTicket, ApiError } from '@/lib/api';
import { TicketPriority } from '@/types/ticket';
import { ArrowLeft, AlertCircle, Save, CheckCircle2 } from 'lucide-react';

export default function CreateTicketPage() {
  const router = useRouter();

  const [formData, setFormData] = useState({
    title: '',
    description: '',
    priority: 'HIGH' as TicketPriority,
    assignee: '',
    category: '',
  });

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [topError, setTopError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Field refs for auto-focusing the first offending field
  const titleRef = useRef<HTMLInputElement>(null);
  const descRef = useRef<HTMLTextAreaElement>(null);
  const priorityRef = useRef<HTMLSelectElement>(null);
  const assigneeRef = useRef<HTMLInputElement>(null);
  const categoryRef = useRef<HTMLInputElement>(null);

  const focusFirstErrorField = (fields: Record<string, string>) => {
    if (fields.title) titleRef.current?.focus();
    else if (fields.description) descRef.current?.focus();
    else if (fields.priority) priorityRef.current?.focus();
    else if (fields.category) categoryRef.current?.focus();
    else if (fields.assignee) assigneeRef.current?.focus();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setFieldErrors({});
    setTopError(null);

    try {
      const created = await createTicket({
        title: formData.title,
        description: formData.description,
        priority: formData.priority,
        assignee: formData.assignee.trim() === '' ? null : formData.assignee,
        category: formData.category,
      });

      // 201 Created -> navigate to ticket detail
      router.push(`/tickets/${created.id}`);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setTopError(err.detail);
        setFieldErrors(err.fields);
        focusFirstErrorField(err.fields);
      } else {
        setTopError('An unexpected network error occurred while submitting the ticket.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Back button */}
      <div>
        <Link
          href="/tickets"
          className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-slate-800 transition"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          <span>Back to Tickets</span>
        </Link>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 shadow-sm space-y-6">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-slate-900">Create New Ticket</h1>
          <p className="text-xs text-slate-500 mt-1">
            New tickets start in <span className="font-semibold text-blue-600">OPEN</span> status and are
            automatically queued for vector embedding.
          </p>
        </div>

        {/* Global Error Banner */}
        {topError && (
          <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800 flex items-start gap-3">
            <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0 mt-0.5" />
            <div className="space-y-1">
              <h3 className="font-semibold">Unable to create ticket</h3>
              <p className="text-xs text-red-700">{topError}</p>
            </div>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Title */}
          <div>
            <label htmlFor="title" className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
              Title <span className="text-red-500">*</span>
            </label>
            <input
              id="title"
              ref={titleRef}
              type="text"
              maxLength={200}
              value={formData.title}
              onChange={(e) => {
                setFormData({ ...formData, title: e.target.value });
                if (fieldErrors.title) {
                  setFieldErrors((prev) => ({ ...prev, title: '' }));
                }
              }}
              placeholder="e.g. Cannot reset SMTP password after mail gateway change"
              className={`w-full rounded-xl border px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 transition ${
                fieldErrors.title
                  ? 'border-red-400 bg-red-50/30 focus:border-red-500 focus:ring-red-500/20'
                  : 'border-slate-200 bg-slate-50/50 focus:border-indigo-500 focus:bg-white focus:ring-indigo-500/20'
              }`}
            />
            {fieldErrors.title ? (
              <p className="mt-1.5 text-xs text-red-600 font-medium flex items-center gap-1">
                <AlertCircle className="h-3.5 w-3.5 flex-shrink-0" />
                <span>{fieldErrors.title}</span>
              </p>
            ) : (
              <p className="mt-1 text-[11px] text-slate-400">Brief summary of the issue (1–200 characters).</p>
            )}
          </div>

          {/* Description */}
          <div>
            <label htmlFor="description" className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
              Description <span className="text-red-500">*</span>
            </label>
            <textarea
              id="description"
              ref={descRef}
              rows={5}
              maxLength={10000}
              value={formData.description}
              onChange={(e) => {
                setFormData({ ...formData, description: e.target.value });
                if (fieldErrors.description) {
                  setFieldErrors((prev) => ({ ...prev, description: '' }));
                }
              }}
              placeholder="Provide complete steps to reproduce, logs, error codes, and affected users..."
              className={`w-full rounded-xl border px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 transition ${
                fieldErrors.description
                  ? 'border-red-400 bg-red-50/30 focus:border-red-500 focus:ring-red-500/20'
                  : 'border-slate-200 bg-slate-50/50 focus:border-indigo-500 focus:bg-white focus:ring-indigo-500/20'
              }`}
            />
            {fieldErrors.description ? (
              <p className="mt-1.5 text-xs text-red-600 font-medium flex items-center gap-1">
                <AlertCircle className="h-3.5 w-3.5 flex-shrink-0" />
                <span>{fieldErrors.description}</span>
              </p>
            ) : (
              <p className="mt-1 text-[11px] text-slate-400">
                Paragraphs will be cleanly partitioned into knowledge chunks for RAG.
              </p>
            )}
          </div>

          {/* Two column row: Priority & Category */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Priority */}
            <div>
              <label htmlFor="priority" className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
                Priority <span className="text-red-500">*</span>
              </label>
              <select
                id="priority"
                ref={priorityRef}
                value={formData.priority}
                onChange={(e) => {
                  setFormData({ ...formData, priority: e.target.value as TicketPriority });
                  if (fieldErrors.priority) {
                    setFieldErrors((prev) => ({ ...prev, priority: '' }));
                  }
                }}
                className={`w-full rounded-xl border px-3.5 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 transition ${
                  fieldErrors.priority
                    ? 'border-red-400 bg-red-50/30 focus:border-red-500 focus:ring-red-500/20'
                    : 'border-slate-200 bg-slate-50/50 focus:border-indigo-500 focus:bg-white focus:ring-indigo-500/20'
                }`}
              >
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
                <option value="URGENT">URGENT</option>
              </select>
              {fieldErrors.priority && (
                <p className="mt-1.5 text-xs text-red-600 font-medium flex items-center gap-1">
                  <AlertCircle className="h-3.5 w-3.5 flex-shrink-0" />
                  <span>{fieldErrors.priority}</span>
                </p>
              )}
            </div>

            {/* Category */}
            <div>
              <label htmlFor="category" className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
                Category <span className="text-red-500">*</span>
              </label>
              <input
                id="category"
                ref={categoryRef}
                type="text"
                maxLength={64}
                value={formData.category}
                onChange={(e) => {
                  setFormData({ ...formData, category: e.target.value });
                  if (fieldErrors.category) {
                    setFieldErrors((prev) => ({ ...prev, category: '' }));
                  }
                }}
                placeholder="e.g. email, network, billing, auth"
                className={`w-full rounded-xl border px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 transition ${
                  fieldErrors.category
                    ? 'border-red-400 bg-red-50/30 focus:border-red-500 focus:ring-red-500/20'
                    : 'border-slate-200 bg-slate-50/50 focus:border-indigo-500 focus:bg-white focus:ring-indigo-500/20'
                }`}
              />
              {fieldErrors.category ? (
                <p className="mt-1.5 text-xs text-red-600 font-medium flex items-center gap-1">
                  <AlertCircle className="h-3.5 w-3.5 flex-shrink-0" />
                  <span>{fieldErrors.category}</span>
                </p>
              ) : (
                <p className="mt-1 text-[11px] text-slate-400">Required metadata (1–64 chars).</p>
              )}
            </div>
          </div>

          {/* Assignee */}
          <div>
            <label htmlFor="assignee" className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
              Assignee <span className="text-slate-400 font-normal">(optional)</span>
            </label>
            <input
              id="assignee"
              ref={assigneeRef}
              type="text"
              maxLength={120}
              value={formData.assignee}
              onChange={(e) => {
                setFormData({ ...formData, assignee: e.target.value });
                if (fieldErrors.assignee) {
                  setFieldErrors((prev) => ({ ...prev, assignee: '' }));
                }
              }}
              placeholder="e.g. Sam, Jordan, Riley"
              className={`w-full rounded-xl border px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 transition ${
                fieldErrors.assignee
                  ? 'border-red-400 bg-red-50/30 focus:border-red-500 focus:ring-red-500/20'
                  : 'border-slate-200 bg-slate-50/50 focus:border-indigo-500 focus:bg-white focus:ring-indigo-500/20'
              }`}
            />
            {fieldErrors.assignee ? (
              <p className="mt-1.5 text-xs text-red-600 font-medium flex items-center gap-1">
                <AlertCircle className="h-3.5 w-3.5 flex-shrink-0" />
                <span>{fieldErrors.assignee}</span>
              </p>
            ) : (
              <p className="mt-1 text-[11px] text-slate-400">Leave blank for unassigned tickets.</p>
            )}
          </div>

          {/* Form Actions */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-100">
            <Link
              href="/tickets"
              className="rounded-xl border border-slate-200 bg-white px-5 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50 transition"
            >
              Cancel
            </Link>
            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex items-center gap-2 rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-medium text-white shadow-sm hover:bg-slate-800 disabled:opacity-50 disabled:cursor-not-allowed transition"
            >
              {isSubmitting ? (
                <>
                  <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  <span>Creating Ticket...</span>
                </>
              ) : (
                <>
                  <Save className="h-4 w-4" />
                  <span>Save Ticket</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
