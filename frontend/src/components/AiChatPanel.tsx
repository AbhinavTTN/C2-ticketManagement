'use client';

import React, { useState, useRef, useEffect } from 'react';
import Link from 'next/link';
import { useAiChat } from './AiChatContext';
import {
  addComment,
  askAi,
  ApiError,
  createTicket,
  fetchTicket,
  transitionTicket,
} from '@/lib/api';
import { continueChatWorkflow, PendingChatAction } from '@/lib/chatWorkflow';
import { parseStatusCommand } from '@/lib/statusCommand';
import { Citation, Ticket, TicketStatus } from '@/types/ticket';
import { MessageSquare, Send, X, AlertCircle, Sparkles, ExternalLink, RefreshCw, ArrowRight } from 'lucide-react';

interface ChatMessage {
  id: string;
  sender: 'user' | 'assistant';
  text: string;
  found?: boolean;
  citations?: Citation[];
  error?: string;
  timestamp: Date;
}

function assistantMessage(text: string, ticket?: Ticket): ChatMessage {
  return {
    id: Math.random().toString(36).substring(7),
    sender: 'assistant',
    text,
    found: Boolean(ticket),
    citations: ticket
      ? [{ ticketId: ticket.id, title: ticket.title, status: ticket.status }]
      : undefined,
    timestamp: new Date(),
  };
}

export default function AiChatPanel() {
  const { isOpen, closePanel, notifyTicketsChanged } = useAiChat();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [inputError, setInputError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [pendingAction, setPendingAction] = useState<PendingChatAction | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [isOpen, messages]);

  if (!isOpen) return null;

  const handleSend = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const trimmed = input.trim();
    if (!trimmed) {
      setInputError('Question is required.');
      return;
    }
    if (trimmed.length > 1000) {
      setInputError('Question must be at most 1000 characters.');
      return;
    }

    setInputError(null);
    const userMsg: ChatMessage = {
      id: Math.random().toString(36).substring(7),
      sender: 'user',
      text: trimmed,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setIsLoading(true);

    const workflow = continueChatWorkflow(trimmed, pendingAction);
    if (workflow.kind !== 'none') {
      if (workflow.kind === 'prompt') {
        setPendingAction(workflow.pending);
        setMessages((prev) => [
          ...prev,
          assistantMessage(workflow.message),
        ]);
        setIsLoading(false);
        return;
      }
      if (workflow.kind === 'cancelled') {
        setPendingAction(null);
        setMessages((prev) => [...prev, assistantMessage(workflow.message)]);
        setIsLoading(false);
        return;
      }

      try {
        if (workflow.kind === 'add-comment') {
          const updated = await addComment(workflow.ticketId, workflow.payload);
          setMessages((prev) => [
            ...prev,
            assistantMessage(
              `Comment added to ticket #${updated.id} by ${workflow.payload.author}.`,
              updated
            ),
          ]);
        } else {
          const created = await createTicket(workflow.payload);
          setMessages((prev) => [
            ...prev,
            assistantMessage(`Created ticket #${created.id}: ${created.title}.`, created),
          ]);
        }
        setPendingAction(null);
        notifyTicketsChanged();
      } catch (err: unknown) {
        const errorMessage = err instanceof ApiError ? err.detail : 'The ticket action could not be completed.';
        setMessages((prev) => [
          ...prev,
          {
            ...assistantMessage(errorMessage),
            error: errorMessage,
          },
        ]);
      } finally {
        setIsLoading(false);
      }
      return;
    }

    const command = parseStatusCommand(trimmed);
    if (command) {
      try {
        const updated = await transitionTicket(command.id, { status: command.status });
        setMessages((prev) => [
          ...prev,
          {
            id: Math.random().toString(36).substring(7),
            sender: 'assistant',
            text: `Ticket #${updated.id} is now ${updated.status}.`,
            found: true,
            citations: [{ ticketId: updated.id, title: updated.title, status: updated.status }],
            timestamp: new Date(),
          },
        ]);
        notifyTicketsChanged();
      } catch (err: unknown) {
        const errorMessage = err instanceof ApiError ? err.detail : 'The status could not be changed.';
        let citations: Citation[] | undefined;
        if (err instanceof ApiError && err.status !== 404) {
          try {
            const current = await fetchTicket(command.id);
            citations = [{ ticketId: current.id, title: current.title, status: current.status }];
          } catch {
            citations = undefined;
          }
        }
        setMessages((prev) => [
          ...prev,
          {
            id: Math.random().toString(36).substring(7),
            sender: 'assistant',
            text: errorMessage,
            error: citations ? undefined : errorMessage,
            found: Boolean(citations),
            citations,
            timestamp: new Date(),
          },
        ]);
      } finally {
        setIsLoading(false);
      }
      return;
    }

    try {
      const response = await askAi(trimmed);
      const assistantMsg: ChatMessage = {
        id: Math.random().toString(36).substring(7),
        sender: 'assistant',
        text: response.answer,
        found: response.found,
        citations: response.citations,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, assistantMsg]);
    } catch (err: unknown) {
      let errorMessage = 'An unexpected error occurred. Please try again.';
      if (err instanceof ApiError) {
        if (err.fields.question) {
          setInputError(err.fields.question);
        }
        errorMessage = err.detail;
      }
      const errorMsg: ChatMessage = {
        id: Math.random().toString(36).substring(7),
        sender: 'assistant',
        text: '',
        error: errorMessage,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setIsLoading(false);
    }
  };

  const getStatusBadge = (status: TicketStatus) => {
    switch (status) {
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

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40 backdrop-blur-sm transition-opacity">
      <div className="relative flex h-full w-full max-w-lg flex-col bg-white shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b px-6 py-4 bg-slate-50">
          <div className="flex items-center gap-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-indigo-600 text-white">
              <Sparkles className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-base font-semibold text-slate-900">Ticket AI Assistant</h2>
              <p className="text-xs text-slate-500">Grounded Q&A over ticket history</p>
            </div>
          </div>
          <div className="flex items-center gap-1">
            {messages.length > 0 && (
              <button
                type="button"
                onClick={() => {
                  setMessages([]);
                  setPendingAction(null);
                }}
                className="rounded-md p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
                title="Clear conversation"
              >
                <RefreshCw className="h-4 w-4" />
              </button>
            )}
            <button
              type="button"
              onClick={closePanel}
              className="rounded-md p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
              title="Close panel"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* Message Thread */}
        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          {messages.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center text-center px-4 text-slate-500">
              <div className="rounded-full bg-indigo-50 p-3 text-indigo-600 mb-3">
                <MessageSquare className="h-6 w-6" />
              </div>
              <p className="text-sm font-medium text-slate-800">Ask questions or manage tickets</p>
              <p className="text-xs text-slate-500 mt-1 max-w-xs">
                Search ticket history, create tickets, add comments, and update allowed statuses.
              </p>
              <div className="mt-6 flex flex-col gap-2 w-full text-left">
                <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">Sample queries:</span>
                {[
                  'What do we know about password reset?',
                  'Who checked the SMTP logs?',
                  'Summarize ticket #1',
                  'Add a comment to ticket #1',
                  'Create a new ticket',
                ].map((sample) => (
                  <button
                    key={sample}
                    type="button"
                    onClick={() => {
                      setInput(sample);
                      setInputError(null);
                    }}
                    className="text-xs rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-slate-700 hover:bg-indigo-50 hover:border-indigo-200 hover:text-indigo-700 transition"
                  >
                    "{sample}"
                  </button>
                ))}
              </div>
            </div>
          ) : (
            messages.map((msg) => (
              <div
                key={msg.id}
                className={`flex flex-col ${msg.sender === 'user' ? 'items-end' : 'items-start'}`}
              >
                {msg.sender === 'user' ? (
                  <div className="max-w-[85%] rounded-2xl rounded-tr-sm bg-indigo-600 px-4 py-2.5 text-sm text-white shadow-sm">
                    {msg.text}
                  </div>
                ) : msg.error ? (
                  <div className="max-w-[95%] rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 space-y-1">
                    <div className="flex items-center gap-1.5 font-semibold text-red-800">
                      <AlertCircle className="h-4 w-4" />
                      <span>Unable to answer</span>
                    </div>
                    <p className="text-xs">{msg.error}</p>
                  </div>
                ) : (
                  <div className="max-w-[95%] space-y-3 rounded-2xl rounded-tl-sm border border-slate-200 bg-slate-50/80 p-4 shadow-sm text-slate-900">
                    <p className="text-sm leading-relaxed whitespace-pre-wrap">{msg.text}</p>

                    {/* Citations section */}
                    {msg.found && (
                      <div className="pt-2 border-t border-slate-200">
                        {msg.citations && msg.citations.length > 0 ? (
                          <div>
                            <span className="text-xs font-semibold uppercase tracking-wider text-slate-500 block mb-2">
                              Cited Tickets ({msg.citations.length})
                            </span>
                            <div className="grid grid-cols-1 gap-1.5">
                              {msg.citations.map((c) => (
                                <CitationCard
                                  key={`${c.ticketId}-${c.status}`}
                                  citation={c}
                                  badgeClass={getStatusBadge(c.status)}
                                  onChanged={(updated) => {
                                    setMessages((prev) =>
                                      prev.map((item) => ({
                                        ...item,
                                        citations: item.citations?.map((cited) =>
                                          cited.ticketId === updated.id
                                            ? { ...cited, title: updated.title, status: updated.status }
                                            : cited
                                        ),
                                      }))
                                    );
                                    notifyTicketsChanged();
                                  }}
                                />
                              ))}
                            </div>
                          </div>
                        ) : (
                          <div className="rounded-lg bg-amber-50 border border-amber-200 p-2.5 text-xs text-amber-800 flex items-center gap-1.5">
                            <AlertCircle className="h-4 w-4 flex-shrink-0" />
                            <span>This answer did not cite a ticket</span>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}
                <span className="mt-1 text-[10px] text-slate-400">
                  {msg.timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </span>
              </div>
            ))
          )}
          {isLoading && (
            <div className="flex items-center gap-2 text-xs text-indigo-600 bg-indigo-50 px-3 py-2 rounded-lg w-fit">
              <Sparkles className="h-3.5 w-3.5 animate-spin" />
              <span>Searching ticket knowledge base...</span>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Input Bar */}
        <div className="border-t bg-white p-4">
          {inputError && (
            <div className="mb-2 text-xs text-red-600 flex items-center gap-1 font-medium">
              <AlertCircle className="h-3.5 w-3.5" />
              <span>{inputError}</span>
            </div>
          )}
          <form onSubmit={handleSend} className="space-y-2">
            <div className="relative flex items-center">
              <textarea
                value={input}
                onChange={(e) => {
                  setInput(e.target.value);
                  if (inputError) setInputError(null);
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    handleSend();
                  }
                }}
                disabled={isLoading}
                placeholder="Ask a question or manage a ticket..."
                rows={2}
                maxLength={1000}
                className="w-full resize-none rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20 disabled:opacity-60"
              />
              <button
                type="submit"
                disabled={isLoading || input.trim().length === 0}
                className="absolute right-2 bottom-2.5 inline-flex items-center justify-center rounded-lg bg-indigo-600 p-2 text-white hover:bg-indigo-700 disabled:bg-slate-200 disabled:text-slate-400 transition"
                title="Send query"
              >
                <Send className="h-4 w-4" />
              </button>
            </div>
            <div className="flex justify-between items-center text-[10px] text-slate-400 px-1">
              <span>Press Enter to send, Shift+Enter for new line</span>
              <span>{input.length}/1000</span>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

function CitationCard({
  citation,
  badgeClass,
  onChanged,
}: {
  citation: Citation;
  badgeClass: string;
  onChanged: (ticket: Ticket) => void;
}) {
  const [allowed, setAllowed] = useState<TicketStatus[]>([]);
  const [status, setStatus] = useState(citation.status);
  const [busy, setBusy] = useState<TicketStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchTicket(citation.ticketId)
      .then((ticket) => {
        if (cancelled) return;
        setStatus(ticket.status);
        setAllowed(ticket.allowedTransitions);
      })
      .catch(() => {
        if (!cancelled) setAllowed([]);
      });
    return () => {
      cancelled = true;
    };
  }, [citation.ticketId, citation.status]);

  const move = async (target: TicketStatus) => {
    setBusy(target);
    setError(null);
    try {
      const updated = await transitionTicket(citation.ticketId, { status: target });
      setStatus(updated.status);
      setAllowed(updated.allowedTransitions);
      onChanged(updated);
    } catch (err: unknown) {
      setError(err instanceof ApiError ? err.detail : 'The status could not be changed.');
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs">
      <Link
        href={`/tickets/${citation.ticketId}`}
        className="group flex items-center justify-between hover:text-indigo-700"
      >
        <div className="flex items-center gap-2 truncate mr-2">
          <span className="font-mono font-medium text-indigo-600">#{citation.ticketId}</span>
          <span className="font-medium text-slate-800 truncate group-hover:text-indigo-900">{citation.title}</span>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <span className={`rounded-full border px-2 py-0.5 text-[10px] font-semibold ${badgeClass}`}>
            {status}
          </span>
          <ExternalLink className="h-3 w-3 text-slate-400 group-hover:text-indigo-600" />
        </div>
      </Link>
      {allowed.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {allowed.map((target) => (
            <button
              key={target}
              type="button"
              disabled={busy !== null}
              onClick={() => move(target)}
              className="inline-flex items-center gap-1 rounded-md bg-slate-900 px-2 py-1 text-[10px] font-medium text-white hover:bg-indigo-600 disabled:opacity-50"
            >
              <ArrowRight className="h-3 w-3" />
              <span>{busy === target ? 'Saving...' : `Move to ${target}`}</span>
            </button>
          ))}
        </div>
      )}
      {error && <p className="mt-1.5 text-[11px] text-red-600">{error}</p>}
    </div>
  );
}
