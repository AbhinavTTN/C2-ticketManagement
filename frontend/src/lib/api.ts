import {
  Ticket,
  TicketPageResponse,
  ProblemDetail,
  CreateTicketPayload,
  UpdateTicketPayload,
  AddCommentPayload,
  TransitionPayload,
  AskResponse,
  TicketStatus,
} from '@/types/ticket';

export class ApiError extends Error {
  status: number;
  problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail || problem.title || `API Error: ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  get fields(): Record<string, string> {
    return this.problem.fields || {};
  }

  get code(): string | undefined {
    return this.problem.code;
  }

  get detail(): string {
    return this.problem.detail || 'An unexpected error occurred.';
  }

  get title(): string {
    return this.problem.title || 'Error';
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {});
  if (!headers.has('Content-Type') && options.body && typeof options.body === 'string') {
    headers.set('Content-Type', 'application/json');
  }

  const res = await fetch(path, {
    ...options,
    headers,
  });

  if (!res.ok) {
    let problem: ProblemDetail;
    try {
      problem = await res.json();
    } catch {
      problem = {
        status: res.status,
        title: res.statusText || 'Error',
        detail: `Request failed with HTTP ${res.status}.`,
      };
    }
    throw new ApiError(res.status, problem);
  }

  if (res.status === 204) {
    return {} as T;
  }

  return res.json();
}

export async function fetchTickets(params: {
  status?: TicketStatus | '';
  q?: string;
  page?: number;
  size?: number;
}): Promise<TicketPageResponse> {
  const query = new URLSearchParams();
  if (params.status) {
    query.set('status', params.status);
  }
  if (params.q && params.q.trim()) {
    query.set('q', params.q.trim());
  }
  if (params.page !== undefined) {
    query.set('page', params.page.toString());
  }
  if (params.size !== undefined) {
    query.set('size', params.size.toString());
  }

  const qs = query.toString();
  return request<TicketPageResponse>(`/api/v1/tickets${qs ? `?${qs}` : ''}`);
}

export async function fetchTicket(id: number | string): Promise<Ticket> {
  return request<Ticket>(`/api/v1/tickets/${id}`);
}

export async function createTicket(payload: CreateTicketPayload): Promise<Ticket> {
  return request<Ticket>('/api/v1/tickets', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function updateTicket(id: number | string, payload: UpdateTicketPayload): Promise<Ticket> {
  return request<Ticket>(`/api/v1/tickets/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export async function addComment(id: number | string, payload: AddCommentPayload): Promise<Ticket> {
  return request<Ticket>(`/api/v1/tickets/${id}/comments`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function transitionTicket(id: number | string, payload: TransitionPayload): Promise<Ticket> {
  return request<Ticket>(`/api/v1/tickets/${id}/status`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function askAi(question: string): Promise<AskResponse> {
  return request<AskResponse>('/api/ai/ask', {
    method: 'POST',
    body: JSON.stringify({ question }),
  });
}
