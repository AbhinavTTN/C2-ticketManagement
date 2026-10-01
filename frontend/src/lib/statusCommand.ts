import { TicketStatus } from '@/types/ticket';

const STATUS_WORDS: Record<string, TicketStatus> = {
  open: 'OPEN',
  'in progress': 'IN_PROGRESS',
  in_progress: 'IN_PROGRESS',
  inprogress: 'IN_PROGRESS',
  resolved: 'RESOLVED',
  closed: 'CLOSED',
  cancelled: 'CANCELLED',
  canceled: 'CANCELLED',
};

const VERB_STATUS: Record<string, TicketStatus> = {
  cancel: 'CANCELLED',
  resolve: 'RESOLVED',
  close: 'CLOSED',
  reopen: 'OPEN',
  start: 'IN_PROGRESS',
};

export interface StatusCommand {
  id: number;
  status: TicketStatus;
}

export function parseStatusCommand(text: string): StatusCommand | null {
  const explicit = text.match(
    /\b(?:change|move|set|mark|transition|update)\b[\s\S]{0,60}?\bticket\s*#?\s*(\d+)\b[\s\S]{0,40}?\b(open|in[\s_]?progress|resolved|closed|cancelled|canceled)\b/i
  );
  if (explicit) {
    return { id: Number(explicit[1]), status: statusFrom(explicit[2]) };
  }

  const verb = text.match(/\b(cancel|resolve|close|reopen|start)\s+ticket\s*#?\s*(\d+)\b/i);
  if (verb) {
    return { id: Number(verb[2]), status: VERB_STATUS[verb[1].toLowerCase()] };
  }

  return null;
}

function statusFrom(word: string): TicketStatus {
  const compact = word.toLowerCase().replace(/[\s_]+/g, ' ');
  return STATUS_WORDS[compact] ?? STATUS_WORDS[compact.replace(/\s+/g, '')];
}
