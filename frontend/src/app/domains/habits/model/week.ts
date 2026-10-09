/**
 * Calendar arithmetic for the week grid, on ISO dates (`2026-10-09`). Every calculation runs in
 * UTC on purpose: a day is a calendar date here, not a moment, and UTC has no daylight saving
 * that could push a date across midnight.
 */

const DAY_MS = 24 * 60 * 60 * 1000;

function toUtc(isoDate: string): Date {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day));
}

function toIso(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/** Whether a string is a calendar date in ISO form, e.g. from the address bar. */
export function isIsoDate(value: string | null | undefined): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && toIso(toUtc(value)) === value;
}

export function addDays(isoDate: string, days: number): string {
  return toIso(new Date(toUtc(isoDate).getTime() + days * DAY_MS));
}

/** The Monday of the calendar week a day lies in. */
export function mondayOf(isoDate: string): string {
  // getUTCDay: Sunday 0 … Saturday 6; Monday is the first day of the week here.
  const weekday = (toUtc(isoDate).getUTCDay() + 6) % 7;
  return addDays(isoDate, -weekday);
}

/** The seven days of the week starting on a Monday. */
export function daysOf(monday: string): string[] {
  return Array.from({ length: 7 }, (_, index) => addDays(monday, index));
}

export function previousWeek(monday: string): string {
  return addDays(monday, -7);
}

export function nextWeek(monday: string): string {
  return addDays(monday, 7);
}

/** ISO 8601 week number: the week with the year's first Thursday is week 1. */
export function isoWeek(isoDate: string): number {
  const thursday = toUtc(addDays(mondayOf(isoDate), 3));
  const yearStart = Date.UTC(thursday.getUTCFullYear(), 0, 1);
  return Math.floor((thursday.getTime() - yearStart) / DAY_MS / 7) + 1;
}

/** Whether a day comes after today; such a day cannot be ticked yet. */
export function isAfter(isoDate: string, today: string): boolean {
  return isoDate > today;
}

/** A calendar date as a Date at noon local time, for the date pipe to format without shifting. */
export function toDisplayDate(isoDate: string): Date {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(year, month - 1, day, 12);
}
