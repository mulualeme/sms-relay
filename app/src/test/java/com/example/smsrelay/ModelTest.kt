package com.example.smsrelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ModelTest {
    private val monNoon = LocalDateTime.of(2026, 10, 26, 12, 0) // a Monday
    private fun sms(body: String, from: String = "+1 (555) 010-0123", name: String? = "Jane Doe") = Event(EventType.SMS, from, name, body, time = 0)

    @Test fun rules() {
        val base = Rule(kind = RuleKind.ADVANCED)
        assertTrue(base.matches(sms("anything"), monNoon))
        assertFalse(base.copy(paused = true).matches(sms("x"), monNoon))
        assertFalse(base.copy(kind = RuleKind.NOTIFICATION).matches(sms("x"), monNoon))
        assertFalse(base.copy(kind = RuleKind.AUTO_REPLY).matches(sms("x"), monNoon))
        assertFalse(base.matches(sms("x").copy(rcs = true), monNoon))
        assertTrue(base.copy(forwardRcs = true).matches(sms("x").copy(rcs = true), monNoon))

        // senders (any match): by name, by digits ignoring formatting; allSenders ignores them
        fun s(vararg c: Condition) = base.copy(allSenders = false, senders = c.toList())
        assertTrue(s(Condition(Filter.Contains, "jane")).matches(sms("x"), monNoon))
        assertTrue(s(Condition(Filter.Contains, "bank"), Condition(Filter.EndsWith, "0123")).matches(sms("x"), monNoon))
        assertTrue(s(Condition(Filter.Contains, "5550100")).matches(sms("x"), monNoon))
        assertFalse(s(Condition(Filter.Contains, "bank")).matches(sms("x"), monNoon))
        assertTrue(base.copy(senders = listOf(Condition(Filter.Contains, "bank"))).matches(sms("x"), monNoon))
        assertTrue(s(Condition(Filter.Contains, "")).matches(sms("x"), monNoon)) // blank filter = no filter

        // groups are OR-ed; inside a group Any/All
        val g1 = Group(true, listOf(Condition(Filter.Contains, "otp"), Condition(Filter.DoesNotContain, "promo")))
        val g2 = Group(false, listOf(Condition(Filter.Regex, "\\b\\d{6}\\b"), Condition(Filter.StartsWith, "zzz")))
        val c = base.copy(allContent = false, groups = listOf(g1, g2))
        assertTrue(c.matches(sms("Your OTP is here"), monNoon))
        assertTrue(c.matches(sms("code 123456 promo"), monNoon))
        assertFalse(c.matches(sms("OTP promo"), monNoon))
        assertTrue(base.copy(allContent = false, groups = listOf(Group())).matches(sms("x"), monNoon)) // empty group = no filter
        assertFalse(base.copy(allContent = false, groups = listOf(Group(conditions = listOf(Condition(Filter.Regex, "(")))))
            .matches(sms("x"), monNoon)) // bad regex never matches, never throws

        // schedule + SIM
        assertFalse(base.copy(days = setOf(2)).matches(sms("x"), monNoon))
        assertTrue(base.copy(allDay = false, start = 9 * 60, end = 17 * 60).matches(sms("x"), monNoon))
        assertFalse(base.copy(allDay = false, start = 13 * 60, end = 17 * 60).matches(sms("x"), monNoon))
        assertFalse(base.copy(receiveSim = 3).matches(sms("x").copy(subId = 2), monNoon))

        // notifications
        val n = Event(EventType.NOTIFICATION, "Title", null, "body", app = "com.whatsapp")
        assertTrue(Rule(kind = RuleKind.NOTIFICATION).matches(n, monNoon))
        assertTrue(Rule(kind = RuleKind.NOTIFICATION, allApps = false, apps = listOf("com.whatsapp")).matches(n, monNoon))
        assertFalse(Rule(kind = RuleKind.NOTIFICATION, allApps = false, apps = listOf("org.telegram")).matches(n, monNoon))
    }

    @Test fun senderFilters() {
        val ethio = Event(EventType.SMS, "+251945287216", "Chris Abebe", "hi", time = 0)
        fun r(f: Filter, v: String) = Rule(kind = RuleKind.BASIC, allSenders = false, senders = listOf(Condition(f, v)))
        // same number in local / international / spaced formats
        assertTrue(r(Filter.ExactlyMatches, "0945287216").matches(ethio, monNoon))
        assertTrue(r(Filter.Contains, "0945287216").matches(ethio, monNoon))
        assertTrue(r(Filter.ExactlyMatches, "+251 94 528 7216").matches(ethio, monNoon))
        assertFalse(r(Filter.ExactlyMatches, "0911223344").matches(ethio, monNoon))
        // contact name
        assertTrue(r(Filter.StartsWith, "chris").matches(ethio, monNoon))
        // "does not contain" must hold for number AND name
        assertFalse(r(Filter.DoesNotContain, "Abebe").matches(ethio, monNoon))
        assertFalse(r(Filter.DoesNotContain, "0945287216").matches(ethio, monNoon))
        assertTrue(r(Filter.DoesNotContain, "CBE").matches(ethio, monNoon))
        // alphanumeric sender ids (banks, telecom)
        val bank = Event(EventType.SMS, "CBEBirr", null, "You received 100 ETB", time = 0)
        assertTrue(r(Filter.Contains, "cbe").matches(bank, monNoon))
        assertFalse(r(Filter.Contains, "telebirr").matches(bank, monNoon))
        assertTrue(r(Filter.DoesNotContain, "telebirr").matches(bank, monNoon))
        // short codes are not treated as phone numbers
        assertFalse(r(Filter.ExactlyMatches, "127").matches(Event(EventType.SMS, "8127", null, "x", time = 0), monNoon))
    }

    @Test fun rejectReasons() {
        val e = Event(EventType.SMS, "1234567890", null, "This is a sample SMS", time = 0)
        val base = Rule(kind = RuleKind.ADVANCED)
        assertEquals(null, base.rejectReason(e, monNoon))
        assertEquals("Sender does not contain CBE", base.copy(allSenders = false, senders = listOf(Condition(Filter.Contains, "CBE"))).rejectReason(e, monNoon))
        assertEquals("Sender does not match any of the conditions",
            base.copy(allSenders = false, senders = listOf(Condition(Filter.Contains, "CBE"), Condition(Filter.Contains, "Bank"))).rejectReason(e, monNoon))
        assertEquals("Message does not contain OTP",
            base.copy(allContent = false, groups = listOf(Group(conditions = listOf(Condition(Filter.Contains, "OTP"))))).rejectReason(e, monNoon))
        assertEquals("Weekday is not selected", base.copy(days = setOf(2)).rejectReason(e, monNoon))
        assertEquals("Rule is currently in paused status.", base.copy(paused = true).rejectReason(e, monNoon))
    }

    @Test fun templates() {
        // matches the original's preview: "From: 1234 (Contact Name) \nThis is a sample SMS \nReceived At: 7/17/2022 4:00 PM "
        assertEquals("From: 1234 (Contact Name) \nThis is a sample SMS \nReceived At: 7/17/2022 4:00 PM ", render(SMS_MESSAGE, SAMPLE, "Rule 1", preview = true))
        assertEquals("$APP_NAME From: 1234 (Contact Name) ", render(SMS_SUBJECT, SAMPLE, "Rule 1"))
        assertEquals("Rule 1 SIM: Carrier Name: 7890hi ", render(listOf(Chip(ChipType.RuleName), Chip(ChipType.SimDetail), Chip(ChipType.Text, "hi")), SAMPLE, "Rule 1"))
        val e = sms("He said \"hi\"\nbye")
        assertEquals("""{"m":"He said \"hi\"\nbye","s":"+1 (555) 010-0123 (Jane Doe)"}""", urlFormat("""{"m":"{msg}","s":"{sender}"}""", e, "r", "", ::jsonEscape))
        assertEquals("Forward message if it contains OTP, code", Group(false, listOf(Condition(value = "OTP"), Condition(value = "code"))).summary())
    }

    @Test fun replies() {
        val e = sms("hello").copy(time = LocalDateTime.of(2026, 10, 26, 16, 15).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        assertEquals("Hi Jane, I'll call you by 5:15 PM on 10/27/2026", replyFormat("Hi {sender_first_name}, I'll call you by {time_12 + 1} on {date+1}", e))
        assertEquals("5550100" to "I'll call back", parseRemoteReply("@5550100 I'll call back"))
        assertEquals("Jane Doe" to "On my way", parseRemoteReply("@(Jane Doe) On my way"))
        assertEquals(null, parseRemoteReply("hello"))
        assertTrue(validPhone("+15555550100")); assertFalse(validPhone("5550100"))
        assertTrue(validEmail("a@b.co, c@d.org")); assertFalse(validEmail("nope"))
    }
}
