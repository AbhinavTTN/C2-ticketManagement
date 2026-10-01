'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import {
  fetchTicket,
  updateTicket,
  addComment,
  transitionTicket,
  ApiError,
} from '@/lib/api';
import { Ticket, TicketStatus, TicketPriority } from '@/types/ticket';
import { useAiChat } from '@/components/AiChatContext';
import {
  ArrowLeft,
  Clock,
  User,
  Tag,
  AlertCircle,
  MessageSquare,
  Sparkles,
  Edit3,
  X,
  Check,
  Send,
  ArrowRight,
  CheckCircle2,
} from 'lucide-react';

export default function TicketDetailPage() {
  const params = useParams();
  const router = useRouter();
  const { openPanel } = useAiChat();

  const id = params?.id as string;

  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [notFoundError, setNotFoundError] = useState<string | null>(null);
  const [bannerError, setBannerError] = useState<{ title: string; detail: string } | null>(null);
  const [successBanner, setSuccessBanner] = useState<string | null>(null);

  // Inline editing state
  const [isEditing, setIsEditing] = useState(false);
  const [editForm, setEditForm] = useState({
    title: '',
    description: '',
    priority: 'HIGH' as TicketPriority,
    assignee: '',
    category: '',
  });
  const [editFieldErrors, setEditFieldErrors] = useState<Record<string, string>>({});
  const [isSavingEdit, setIsSavingEdit] = useState(false);

  // Comment form state
  const [commentAuthor, setCommentAuthor] = useState('');
  const [commentBody, setCommentBody] = useState('');
  const [commentFieldErrors, setCommentFieldErrors] = useState<Record<string, string>>({});
  const [isSubmittingComment, setIsSubmittingComment] = useState(false);

  // Transition in-flight state
  const [isTransitioning, setIsTransitioning] = useState<TicketStatus | null>(null);

  const loadTicketData = useCallback(async () => {
    if (!id) return;
    setIsLoading(true);
    setBannerError(null);
    setNotFoundError(null);
    try {
      const data = await fetchTicket(id);
      setTicket(data);
      setEditForm({
        title: data.title,
        description: data.description,
        priority: data.priority,
        assignee: data.assignee || '',
        category: data.category,
      });
    } catch (err: unknown) {
      if (err instanceof ApiError && err.status === 404) {
        setNotFoundError(err.detail);
      } else if (err instanceof ApiError) {
        setBannerError({ title: err.title, detail: err.detail });
      } else {
        setBannerError({
          title: 'Error loading ticket',
          detail: 'Failed to connect to the backend service.',
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadTicketData();
  }, [loadTicketData]);

  // Handle inline edit save
  const handleSaveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!ticket) return;
    setIsSavingEdit(true);
    setEditFieldErrors({});
    setBannerError(null);
    setSuccessBanner(null);

    try {
      const updated = await updateTicket(ticket.id, {
        title: editForm.title,
        description: editForm.description,
        priority: editForm.priority,
        assignee: editForm.assignee.trim() === '' ? '' : editForm.assignee,
        category: editForm.category,
      });
      setTicket(updated);
      setIsEditing(false);
      setSuccessBanner('Ticket details updated successfully.');
      setTimeout(() => setSuccessBanner(null), 4000);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setBannerError({ title: err.title, detail: err.detail });
        setEditFieldErrors(err.fields);
      } else {
        setBannerError({
          title: 'Update failed',
          detail: 'An unexpected network error occurred.',
        });
      }
    } finally {
      setIsSavingEdit(false);
    }
  };

  // Handle status transition
  const handleTransition = async (targetStatus: TicketStatus) => {
    if (!ticket) return;
    setIsTransitioning(targetStatus);
    setBannerError(null);
    setSuccessBanner(null);

    try {
      const updated = await transitionTicket(ticket.id, { status: targetStatus });
      setTicket(updated);
      setSuccessBanner(`Ticket transitioned to ${targetStatus}.`);
      setTimeout(() => setSuccessBanner(null), 4000);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setBannerError({ title: err.title, detail: err.detail });
      } else {
        setBannerError({
          title: 'Transition failed',
          detail: 'Could not transition ticket status.',
        });
      }
    } finally {
      setIsTransitioning(null);
    }
  };

  // Handle add comment
  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!ticket) return;
    setIsSubmittingComment(true);
    setCommentFieldErrors({});
    setBannerError(null);

    try {
      const updated = await addComment(ticket.id, {
        author: commentAuthor,
        body: commentBody,
      });
      setTicket(updated);
      setCommentAuthor('');
      setCommentBody('');
      setSuccessBanner('Comment added and queued for knowledge re-embedding.');
      setTimeout(() => setSuccessBanner(null), 4000);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setBannerError({ title: err.title, detail: err.detail });
        setCommentFieldErrors(err.fields);
      } else {
        setBannerError({
          title: 'Comment failed',
          detail: 'Failed to post comment to the backend.',
        });
      }
    } finally {
      setIsSubmittingComment(false);
    }
  };

  const getStatusBadge = (s: TicketStatus) => {
    switch (s) {
      case 'OPEN':
        return 'bg-blue-100 text-blue-800 border-blue-200';
      case 'IN_PROGRESS':
        return 'bg-amber-100 text-amber-800 border-amber-200';
      case 'RESOLVED':
        return 'bg-green-100 text-green-800 border-green-200';
      case 'CLOSED':
        return 'bg-slate-100 text-slate-700 border-slate-300';
      case 'CANCELLED':
        return 'bg-rose-100 text-rose-800 border-rose-200';
      default:
        return 'bg-gray-100 text-gray-800 border-gray-200';
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

  if (isLoading) {
    return (
      <div className="flex h-96 flex-col items-center justify-center text-slate-400 space-y-3">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-indigo-600 border-t-transparent" />
        <span className="text-sm font-medium text-slate-500">Loading ticket details...</span>
      </div>
    );
  }

  if (notFoundError) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16 text-center space-y-4">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-red-100 text-red-600">
          <AlertCircle className="h-8 w-8" />
        </div>
        <h1 className="text-2xl font-bold text-slate-900">Ticket Not Found</h1>
        <p className="text-sm text-slate-600 max-w-md mx-auto">{notFoundError}</p>
        <div className="pt-4">
          <Link
            href="/tickets"
            className="inline-flex items-center gap-2 rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-medium text-white hover:bg-slate-800 transition"
          >
            <ArrowLeft className="h-4 w-4" />
            <span>Return to Tickets</span>
          </Link>
        </div>
      </div>
    );
  }

  if (!ticket) return null;

  return (
    <div className="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      {/* Back button and AI launcher */}
      <div className="flex items-center justify-between">
        <Link
          href="/tickets"
          className="inline-flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-slate-800 transition"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          <span>Back to All Tickets</span>
        </Link>
        <button
          type="button"
          onClick={() => openPanel()}
          className="inline-flex items-center gap-1.5 rounded-xl border border-indigo-200 bg-indigo-50/50 px-3 py-1.5 text-xs font-medium text-indigo-700 hover:bg-indigo-100 transition"
        >
          <Sparkles className="h-3.5 w-3.5 text-indigo-600" />
          <span>Ask AI about this ticket</span>
        </button>
      </div>

      {/* Success Notification */}
      {successBanner && (
        <div className="rounded-xl border border-green-200 bg-green-50 p-4 text-sm text-green-800 flex items-center gap-2.5">
          <CheckCircle2 className="h-5 w-5 text-green-600 flex-shrink-0" />
          <span>{successBanner}</span>
        </div>
      )}

      {/* Error Banner */}
      {bannerError && (
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800 flex items-start gap-3">
          <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0 mt-0.5" />
          <div className="space-y-1">
            <h3 className="font-semibold">{bannerError.title}</h3>
            <p className="text-xs text-red-700">{bannerError.detail}</p>
          </div>
        </div>
      )}

      {/* Main Ticket Card */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 shadow-sm space-y-6">
        {/* Header with Title and Edit toggle */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between border-b border-slate-100 pb-6">
          <div className="space-y-2 flex-1 mr-4">
            <div className="flex items-center gap-3">
              <span className="font-mono text-sm font-semibold text-slate-400">
                Ticket #{ticket.id}
              </span>
              <span
                className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${getStatusBadge(
                  ticket.status
                )}`}
              >
                {ticket.status}
              </span>
              <span
                className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs ${getPriorityBadge(
                  ticket.priority
                )}`}
              >
                {ticket.priority}
              </span>
            </div>
            {!isEditing && (
              <h1 className="text-2xl font-bold tracking-tight text-slate-900 leading-tight">
                {ticket.title}
              </h1>
            )}
          </div>

          <div className="flex items-center gap-2">
            {!isEditing ? (
              <button
                type="button"
                onClick={() => setIsEditing(true)}
                className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2 text-xs font-medium text-slate-700 hover:bg-slate-100 transition"
              >
                <Edit3 className="h-3.5 w-3.5 text-slate-500" />
                <span>Edit Fields</span>
              </button>
            ) : (
              <button
                type="button"
                onClick={() => {
                  setIsEditing(false);
                  setEditFieldErrors({});
                  setEditForm({
                    title: ticket.title,
                    description: ticket.description,
                    priority: ticket.priority,
                    assignee: ticket.assignee || '',
                    category: ticket.category,
                  });
                }}
                className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50 transition"
              >
                <X className="h-3.5 w-3.5 text-slate-500" />
                <span>Cancel Editing</span>
              </button>
            )}
          </div>
        </div>

        {/* Status Transition Action Bar */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 rounded-xl border border-slate-100 bg-slate-50/70 p-4 text-xs">
          <div className="flex items-center gap-2">
            <span className="font-semibold text-slate-600">Workflow Transitions:</span>
            {ticket.allowedTransitions.length === 0 ? (
              <span className="italic text-slate-400">
                Ticket is {ticket.status} (no further transitions permitted)
              </span>
            ) : (
              <span className="text-slate-500">
                Move from <span className="font-medium text-slate-700">{ticket.status}</span> to:
              </span>
            )}
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            {ticket.allowedTransitions.map((target) => (
              <button
                key={target}
                type="button"
                disabled={isTransitioning !== null}
                onClick={() => handleTransition(target)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-slate-900 px-3 py-1.5 font-medium text-white shadow-sm hover:bg-indigo-600 disabled:opacity-50 transition"
              >
                {isTransitioning === target ? (
                  <div className="h-3 w-3 animate-spin rounded-full border border-white border-t-transparent" />
                ) : (
                  <ArrowRight className="h-3 w-3" />
                )}
                <span>Move to {target}</span>
              </button>
            ))}
          </div>
        </div>

        {/* View Details vs Inline Edit Form */}
        {isEditing ? (
          <form onSubmit={handleSaveEdit} className="space-y-4 pt-2">
            {/* Title */}
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1">
                Title
              </label>
              <input
                type="text"
                maxLength={200}
                value={editForm.title}
                onChange={(e) => setEditForm({ ...editForm, title: e.target.value })}
                className={`w-full rounded-xl border px-3.5 py-2 text-sm text-slate-900 ${
                  editFieldErrors.title ? 'border-red-400 bg-red-50/20' : 'border-slate-200 bg-slate-50'
                }`}
              />
              {editFieldErrors.title && (
                <p className="mt-1 text-xs text-red-600">{editFieldErrors.title}</p>
              )}
            </div>

            {/* Description */}
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1">
                Description
              </label>
              <textarea
                rows={5}
                maxLength={10000}
                value={editForm.description}
                onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                className={`w-full rounded-xl border px-3.5 py-2 text-sm text-slate-900 ${
                  editFieldErrors.description ? 'border-red-400 bg-red-50/20' : 'border-slate-200 bg-slate-50'
                }`}
              />
              {editFieldErrors.description && (
                <p className="mt-1 text-xs text-red-600">{editFieldErrors.description}</p>
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              {/* Priority */}
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1">
                  Priority
                </label>
                <select
                  value={editForm.priority}
                  onChange={(e) =>
                    setEditForm({ ...editForm, priority: e.target.value as TicketPriority })
                  }
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-900"
                >
                  <option value="LOW">LOW</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="HIGH">HIGH</option>
                  <option value="URGENT">URGENT</option>
                </select>
              </div>

              {/* Category */}
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1">
                  Category
                </label>
                <input
                  type="text"
                  maxLength={64}
                  value={editForm.category}
                  onChange={(e) => setEditForm({ ...editForm, category: e.target.value })}
                  className={`w-full rounded-xl border px-3 py-2 text-sm text-slate-900 ${
                    editFieldErrors.category ? 'border-red-400 bg-red-50/20' : 'border-slate-200 bg-slate-50'
                  }`}
                />
                {editFieldErrors.category && (
                  <p className="mt-1 text-xs text-red-600">{editFieldErrors.category}</p>
                )}
              </div>

              {/* Assignee */}
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1">
                  Assignee
                </label>
                <input
                  type="text"
                  maxLength={120}
                  value={editForm.assignee}
                  onChange={(e) => setEditForm({ ...editForm, assignee: e.target.value })}
                  placeholder="Leave empty to clear"
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-900"
                />
              </div>
            </div>

            <div className="pt-2 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setIsEditing(false)}
                className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={isSavingEdit}
                className="inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-4 py-2 text-xs font-medium text-white hover:bg-slate-800 disabled:opacity-50"
              >
                {isSavingEdit ? (
                  <div className="h-3.5 w-3.5 animate-spin rounded-full border border-white border-t-transparent" />
                ) : (
                  <Check className="h-3.5 w-3.5" />
                )}
                <span>Save Changes</span>
              </button>
            </div>
          </form>
        ) : (
          <div className="space-y-6">
            {/* Metadata Bar */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 py-3 px-4 rounded-xl bg-slate-50 text-xs">
              <div>
                <span className="text-slate-400 block mb-0.5">Assignee</span>
                <span className="font-medium text-slate-800 flex items-center gap-1">
                  <User className="h-3.5 w-3.5 text-slate-400" />
                  {ticket.assignee || <span className="italic text-slate-400">Unassigned</span>}
                </span>
              </div>
              <div>
                <span className="text-slate-400 block mb-0.5">Category</span>
                <span className="font-mono text-slate-800 font-medium flex items-center gap-1">
                  <Tag className="h-3.5 w-3.5 text-slate-400" />
                  {ticket.category}
                </span>
              </div>
              <div>
                <span className="text-slate-400 block mb-0.5">Created</span>
                <span className="text-slate-700 flex items-center gap-1">
                  <Clock className="h-3.5 w-3.5 text-slate-400" />
                  {formatDate(ticket.createdAt)}
                </span>
              </div>
              <div>
                <span className="text-slate-400 block mb-0.5">Last Updated</span>
                <span className="text-slate-700 flex items-center gap-1">
                  <Clock className="h-3.5 w-3.5 text-slate-400" />
                  {formatDate(ticket.updatedAt)}
                </span>
              </div>
            </div>

            {/* Description Body */}
            <div>
              <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-2">
                Description
              </h3>
              <div className="rounded-xl border border-slate-100 bg-slate-50/40 p-4 text-sm text-slate-800 whitespace-pre-wrap leading-relaxed">
                {ticket.description}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Comments Section */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 shadow-sm space-y-6">
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-2">
            <MessageSquare className="h-5 w-5 text-indigo-600" />
            <h2 className="text-lg font-bold text-slate-900">
              Activity & Comments ({ticket.comments.length})
            </h2>
          </div>
          <span className="text-xs text-slate-400">Oldest first</span>
        </div>

        {/* Comment List */}
        <div className="space-y-4">
          {ticket.comments.length === 0 ? (
            <div className="rounded-xl border border-dashed border-slate-200 p-8 text-center text-xs text-slate-400">
              No comments yet. Add the first update below.
            </div>
          ) : (
            ticket.comments.map((comment) => (
              <div
                key={comment.id}
                className="rounded-xl border border-slate-100 bg-slate-50/50 p-4 space-y-2 text-sm"
              >
                <div className="flex items-center justify-between text-xs">
                  <span className="font-semibold text-slate-800 flex items-center gap-1.5">
                    <User className="h-3.5 w-3.5 text-slate-400" />
                    {comment.author}
                  </span>
                  <span className="text-slate-400 flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {formatDate(comment.createdAt)}
                  </span>
                </div>
                <p className="text-slate-700 whitespace-pre-wrap leading-relaxed">{comment.body}</p>
              </div>
            ))
          )}
        </div>

        {/* Add Comment Form */}
        <form onSubmit={handleAddComment} className="pt-4 border-t border-slate-100 space-y-4">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-700">
            Add Comment / Resolution Note
          </h3>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="sm:col-span-1">
              <label htmlFor="commentAuthor" className="block text-xs font-medium text-slate-600 mb-1">
                Your Name / Author <span className="text-red-500">*</span>
              </label>
              <input
                id="commentAuthor"
                type="text"
                maxLength={120}
                value={commentAuthor}
                onChange={(e) => setCommentAuthor(e.target.value)}
                placeholder="e.g. Jordan"
                className={`w-full rounded-xl border px-3 py-2 text-xs text-slate-900 ${
                  commentFieldErrors.author ? 'border-red-400 bg-red-50/20' : 'border-slate-200 bg-slate-50'
                }`}
              />
              {commentFieldErrors.author && (
                <p className="mt-1 text-xs text-red-600">{commentFieldErrors.author}</p>
              )}
            </div>

            <div className="sm:col-span-2">
              <label htmlFor="commentBody" className="block text-xs font-medium text-slate-600 mb-1">
                Comment Body <span className="text-red-500">*</span>
              </label>
              <textarea
                id="commentBody"
                rows={3}
                maxLength={10000}
                value={commentBody}
                onChange={(e) => setCommentBody(e.target.value)}
                placeholder="Document troubleshooting steps, root cause, or resolution..."
                className={`w-full rounded-xl border px-3 py-2 text-xs text-slate-900 ${
                  commentFieldErrors.body ? 'border-red-400 bg-red-50/20' : 'border-slate-200 bg-slate-50'
                }`}
              />
              {commentFieldErrors.body && (
                <p className="mt-1 text-xs text-red-600">{commentFieldErrors.body}</p>
              )}
            </div>
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={isSubmittingComment}
              className="inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-4 py-2 text-xs font-medium text-white shadow-sm hover:bg-slate-800 disabled:opacity-50 transition"
            >
              {isSubmittingComment ? (
                <>
                  <div className="h-3.5 w-3.5 animate-spin rounded-full border border-white border-t-transparent" />
                  <span>Posting Comment...</span>
                </>
              ) : (
                <>
                  <Send className="h-3.5 w-3.5" />
                  <span>Post Comment</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
