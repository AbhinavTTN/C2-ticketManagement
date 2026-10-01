import {
  AddCommentPayload,
  CreateTicketPayload,
  TicketPriority,
} from '@/types/ticket';

type CreateFields = Partial<CreateTicketPayload>;

export type PendingChatAction =
  | {
      kind: 'comment';
      ticketId?: number;
      author?: string;
      body?: string;
      awaiting?: 'ticketId' | 'body' | 'author';
    }
  | {
      kind: 'create';
      fields: CreateFields;
      awaiting?: 'title' | 'description' | 'priority' | 'category';
    };

export type ChatWorkflowResult =
  | { kind: 'none' }
  | { kind: 'prompt'; pending: PendingChatAction; message: string }
  | { kind: 'add-comment'; ticketId: number; payload: AddCommentPayload }
  | { kind: 'create-ticket'; payload: CreateTicketPayload }
  | { kind: 'cancelled'; message: string };

const PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

export function continueChatWorkflow(
  text: string,
  pending: PendingChatAction | null
): ChatWorkflowResult {
  const value = text.trim();
  if (pending && /^(cancel|never mind|nevermind|stop)$/i.test(value)) {
    return { kind: 'cancelled', message: 'Okay, I cancelled that action.' };
  }

  if (pending?.kind === 'comment') {
    return continueComment(value, pending);
  }
  if (pending?.kind === 'create') {
    return continueCreate(value, pending);
  }
  if (isCommentCommand(value)) {
    return continueComment(value, parseComment(value));
  }
  if (isCreateCommand(value)) {
    return continueCreate(value, { kind: 'create', fields: parseCreateFields(value) });
  }
  return { kind: 'none' };
}

function continueComment(text: string, current: Extract<PendingChatAction, { kind: 'comment' }>): ChatWorkflowResult {
  const next = { ...current };
  if (current.awaiting === 'ticketId') {
    next.ticketId = ticketId(text);
  } else if (current.awaiting === 'body') {
    next.body = field(text, 'comment') ?? field(text, 'body') ?? cleanAnswer(text);
  } else if (current.awaiting === 'author') {
    next.author = author(text) ?? cleanAnswer(text);
  }

  if (!next.ticketId) {
    return {
      kind: 'prompt',
      pending: { ...next, awaiting: 'ticketId' },
      message: 'Which ticket ID should I add the comment to?',
    };
  }
  if (!next.body) {
    return {
      kind: 'prompt',
      pending: { ...next, awaiting: 'body' },
      message: `What comment should I add to ticket #${next.ticketId}?`,
    };
  }
  if (!next.author) {
    return {
      kind: 'prompt',
      pending: { ...next, awaiting: 'author' },
      message: 'What is your name? It is required as the comment author.',
    };
  }
  return {
    kind: 'add-comment',
    ticketId: next.ticketId,
    payload: { author: next.author, body: next.body },
  };
}

function continueCreate(text: string, current: Extract<PendingChatAction, { kind: 'create' }>): ChatWorkflowResult {
  const parsed = parseCreateFields(text);
  if (current.awaiting && Object.keys(parsed).length === 0) {
    if (current.awaiting === 'priority') {
      parsed.priority = priority(text);
    } else {
      parsed[current.awaiting] = cleanAnswer(text);
    }
  }
  const fields = { ...current.fields, ...parsed };
  const missing = missingCreateField(fields);

  if (missing === 'title') {
    return {
      kind: 'prompt',
      pending: { kind: 'create', fields, awaiting: 'title' },
      message: 'What is the ticket title?',
    };
  }
  if (missing === 'description') {
    return {
      kind: 'prompt',
      pending: { kind: 'create', fields, awaiting: 'description' },
      message: 'What is the ticket description?',
    };
  }
  if (missing === 'priority') {
    return {
      kind: 'prompt',
      pending: { kind: 'create', fields, awaiting: 'priority' },
      message: 'What priority should it have: LOW, MEDIUM, HIGH, or URGENT?',
    };
  }
  if (missing === 'category') {
    return {
      kind: 'prompt',
      pending: { kind: 'create', fields, awaiting: 'category' },
      message: 'What category should the ticket use?',
    };
  }

  return {
    kind: 'create-ticket',
    payload: {
      title: fields.title!,
      description: fields.description!,
      priority: fields.priority!,
      category: fields.category!,
      assignee: fields.assignee ?? null,
    },
  };
}

function parseComment(text: string): Extract<PendingChatAction, { kind: 'comment' }> {
  const authorName = author(text);
  let body = field(text, 'comment') ?? field(text, 'body');
  if (!body) {
    body = text
      .replace(/\b(?:add|post|leave|write)\s+(?:a\s+)?comment\b/i, '')
      .replace(/\b(?:to|on|for)\s+ticket\s*(?:id\s*)?#?\s*\d+\b/i, '')
      .replace(/\b(?:author|name)\s*[:=]\s*[^,;\n]+/i, '')
      .replace(/\s+\bby\s+[A-Za-z][A-Za-z .'-]*$/i, '')
      .replace(/^[\s:,-]+|[\s,;-]+$/g, '')
      .trim();
  }
  return {
    kind: 'comment',
    ticketId: ticketId(text),
    author: authorName,
    body: body || undefined,
  };
}

function parseCreateFields(text: string): CreateFields {
  const fields: CreateFields = {};
  fields.title = field(text, 'title');
  fields.description = field(text, 'description');
  fields.category = field(text, 'category');
  fields.assignee = field(text, 'assignee') ?? field(text, 'assigned to');
  fields.priority = priority(field(text, 'priority') ?? text);

  if (!fields.title) {
    const titled = text.match(/\b(?:titled|called)\s+["']?(.+?)["']?(?=\s+(?:with|description|priority|category|assigned)\b|$)/i);
    fields.title = titled?.[1]?.trim();
  }
  return Object.fromEntries(
    Object.entries(fields).filter(([, value]) => value !== undefined && value !== '')
  ) as CreateFields;
}

function field(text: string, name: string): string | undefined {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = text.match(
    new RegExp(`(?:^|[,;\\n])\\s*${escaped}\\s*[:=]\\s*(.+?)(?=\\s*(?:[,;\\n]|$))`, 'i')
  );
  return match?.[1]?.trim().replace(/^["']|["']$/g, '') || undefined;
}

function author(text: string): string | undefined {
  const labelled = field(text, 'author') ?? field(text, 'name');
  if (labelled) return labelled;
  const named = text.match(/\b(?:my name is|i am|i'm)\s+([A-Za-z][A-Za-z .'-]*)$/i);
  if (named) return named[1].trim();
  const trailing = text.match(/\s+\bby\s+([A-Za-z][A-Za-z .'-]*)$/i);
  return trailing?.[1]?.trim();
}

function ticketId(text: string): number | undefined {
  const match = text.match(/\bticket\s*(?:id\s*)?#?\s*(\d+)\b/i);
  if (match) return Number(match[1]);
  const onlyNumber = text.match(/^\s*#?(\d+)\s*$/);
  return onlyNumber ? Number(onlyNumber[1]) : undefined;
}

function priority(text: string): TicketPriority | undefined {
  const match = text.match(/\b(low|medium|high|urgent)\b/i);
  const value = match?.[1]?.toUpperCase() as TicketPriority | undefined;
  return value && PRIORITIES.includes(value) ? value : undefined;
}

function missingCreateField(fields: CreateFields): 'title' | 'description' | 'priority' | 'category' | null {
  if (!fields.title) return 'title';
  if (!fields.description) return 'description';
  if (!fields.priority) return 'priority';
  if (!fields.category) return 'category';
  return null;
}

function isCommentCommand(text: string): boolean {
  return /\b(?:add|post|leave|write)\s+(?:a\s+)?comment\b/i.test(text);
}

function isCreateCommand(text: string): boolean {
  return /\b(?:create|open|raise|file)\s+(?:a\s+)?(?:new\s+)?ticket\b/i.test(text);
}

function cleanAnswer(text: string): string {
  return text
    .replace(/^(?:title|description|priority|category|assignee|author|name|comment|body)\s*[:=]\s*/i, '')
    .trim();
}
