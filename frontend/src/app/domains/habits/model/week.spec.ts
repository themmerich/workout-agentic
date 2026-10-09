import { addDays, daysOf, isAfter, isIsoDate, isoWeek, mondayOf, nextWeek, previousWeek, toDisplayDate } from './week';

describe('week', () => {
  it('finds the Monday of a week from any of its days, Sunday included', () => {
    expect(mondayOf('2026-10-05')).toBe('2026-10-05');
    expect(mondayOf('2026-10-09')).toBe('2026-10-05');
    expect(mondayOf('2026-10-11')).toBe('2026-10-05');
    expect(mondayOf('2026-10-12')).toBe('2026-10-12');
  });

  it('lists the seven days of a week and steps across months and years', () => {
    expect(daysOf('2026-09-28')).toEqual([
      '2026-09-28',
      '2026-09-29',
      '2026-09-30',
      '2026-10-01',
      '2026-10-02',
      '2026-10-03',
      '2026-10-04',
    ]);
    expect(nextWeek('2026-12-28')).toBe('2027-01-04');
    expect(previousWeek('2027-01-04')).toBe('2026-12-28');
    // Across the switch to winter time, a day is still a day.
    expect(addDays('2026-10-24', 2)).toBe('2026-10-26');
  });

  it('numbers the weeks the ISO way, across the turn of the year', () => {
    expect(isoWeek('2026-10-09')).toBe(41);
    // 2026 ends on a Thursday: its last days belong to week 53.
    expect(isoWeek('2026-12-31')).toBe(53);
    expect(isoWeek('2027-01-03')).toBe(53);
    // Week 1 of 2027 begins on Monday, 4 January.
    expect(isoWeek('2027-01-04')).toBe(1);
    // 1 January 2025 is a Wednesday and belongs to week 1 of 2025.
    expect(isoWeek('2025-01-01')).toBe(1);
  });

  it('tells a day still to come from today and the days before', () => {
    expect(isAfter('2026-10-10', '2026-10-09')).toBe(true);
    expect(isAfter('2026-10-09', '2026-10-09')).toBe(false);
    expect(isAfter('2025-12-31', '2026-10-09')).toBe(false);
  });

  it('accepts only real calendar dates as week parameter', () => {
    expect(isIsoDate('2026-10-05')).toBe(true);
    expect(isIsoDate('2026-02-30')).toBe(false);
    expect(isIsoDate('05.10.2026')).toBe(false);
    expect(isIsoDate(undefined)).toBe(false);
  });

  it('hands the date pipe the calendar day it stands for', () => {
    const date = toDisplayDate('2026-10-09');
    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 9, 9]);
  });
});
