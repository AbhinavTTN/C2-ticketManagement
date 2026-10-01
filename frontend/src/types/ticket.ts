export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'CANCELLED';

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface Comment {
  id: number;
  author: string;
  body: string;
  createdAt: string;
}

export interface Ticket {
  id: number;
  title: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  assignee: string | null;
  category: string;
  createdAt: string;
  updatedAt: string;
  comments: Comment[];
  allowedTransitions: TicketStatus[];
}

export interface TicketSummary {
  id: number;
  title: string;
  status: TicketStatus;
  priority: TicketPriority;
  assignee: string | null;
  category: string;
  updatedAt: string;
}

export interface TicketPageResponse {
  content: TicketSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail: string;
  instance?: string;
  code?: string;
  fields?: Record<string, string>;
}

export interface CreateTicketPayload {
  title: string;
  description: string;
  priority: TicketPriority;
  assignee?: string | null;
  category: string;
}

export interface UpdateTicketPayload {
  title?: string;
  description?: string;
  priority?: TicketPriority;
  assignee?: string | null;
  category?: string;
}

export interface AddCommentPayload {
  author: string;
  body: string;
}

export interface TransitionPayload {
  status: TicketStatus;
}

export interface Citation {
  ticketId: number;
  title: string;
  status: TicketStatus;
}

export interface AskResponse {
  question: string;
  found: boolean;
  answer: string;
  citations: Citation[];
}
