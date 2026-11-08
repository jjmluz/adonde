import AdondeCore
import SwiftUI

/// The yellow tag on the card of a player who has just made the closest guess ever.
struct NewRecordTag: View {
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "trophy").font(.system(size: 11, weight: .bold))
            Text(verbatim: tr("new_record").uppercased()).font(.system(size: 11, weight: .bold))
        }
        .foregroundStyle(Color.background)
        .padding(.leading, 7)
        .padding(.trailing, 9)
        .padding(.vertical, 2)
        .background(Color.accent, in: Capsule())
    }
}

/// The records a game broke, for the final results.
struct NewRecordsCard: View {
    let records: [NewRecord]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "trophy").font(.system(size: 17, weight: .semibold)).foregroundStyle(Color.accent)
                LabelText(tr("new_records"), 17, .accent, .bold)
            }
            ForEach(Array(records.enumerated()), id: \.offset) { _, record in
                RecordRow(holder: record.holder, detail: detail(record), value: value(record), avatarSize: 30, nameSize: 15)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.card, in: RoundedRectangle(cornerRadius: 10))
        .overlay { RoundedRectangle(cornerRadius: 10).strokeBorder(Color.accent, lineWidth: 2) }
    }

    private func detail(_ record: NewRecord) -> String {
        switch record.kind {
        case .closest: [tr("closest_ever"), countryName(record.country)].filter { !$0.isEmpty }.joined(separator: " · ")
        case .wins: tr("most_wins")
        default: tr("close_rounds")
        }
    }

    private func value(_ record: NewRecord) -> String {
        record.kind == .closest ? km(record.value) : "\(Int(record.value))"
    }
}

/// One of the records screen's lists: a yellow title, then its rows, or a line saying there are none yet.
struct RecordList<Rows: View>: View {
    let title: String
    let empty: Bool
    @ViewBuilder let rows: Rows

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            LabelText(title, 17, .accent, .bold)
            if empty { LabelText(tr("no_records"), 15, .muted) } else { rows }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .background(Color.card, in: RoundedRectangle(cornerRadius: 10))
    }
}

/// The closest guesses ever, numbered, with where and when.
struct ClosestList: View {
    let records: Records

    var body: some View {
        let now = today()
        RecordList(title: tr("closest_ever"), empty: records.closest.isEmpty) {
            ForEach(Array(records.closest.enumerated()), id: \.offset) { index, guess in
                let detail = [countryName(guess.country), dayLabel(guess.day, today: now)].filter { !$0.isEmpty }
                RecordRow(holder: guess.holder, detail: detail.joined(separator: " · "), value: km(guess.km), rank: index + 1)
            }
        }
    }
}

/// A count for each player, most first: wins or close rounds. Only the first few fit.
struct TallyList: View {
    let title: String
    let tallies: [Tally]
    let count: (Tally) -> Int32

    var body: some View {
        RecordList(title: title, empty: tallies.isEmpty) {
            ForEach(Array(tallies.prefix(Int(RecordsKt.CLOSEST_KEPT)).enumerated()), id: \.offset) { _, tally in
                RecordRow(holder: tally.holder, detail: "", value: "\(count(tally))")
            }
        }
    }
}

/// A holder's avatar and name, a `detail` under the name if there is one, and `value` at the end.
struct RecordRow: View {
    let holder: Holder
    let detail: String
    let value: String
    var avatarSize: CGFloat = 32
    var nameSize: CGFloat = 16
    var rank: Int?

    var body: some View {
        HStack(spacing: 10) {
            if let rank { LabelText("\(rank)", 15, .muted, .bold).frame(width: 14) }
            // Saved records come from text, so they're kept to the avatars and colours there are.
            AvatarView(
                avatar: min(max(Int(holder.avatar), 0), avatars.count - 1),
                colorIndex: min(max(Int(holder.slot), 0), playerColors.count - 1),
                size: avatarSize
            )
            VStack(alignment: .leading, spacing: 0) {
                NameText(holder.name, size: nameSize)
                // Left-aligned, as it can wrap onto a second line in a narrow card.
                if !detail.isEmpty { LabelText(detail, 13, .muted).multilineTextAlignment(.leading) }
            }
            Spacer(minLength: 0)
            LabelText(value, 17, .foreground, .bold)
        }
    }
}

/// A country's name in the language of the game's text, from its two-letter code.
func countryName(_ code: String) -> String {
    code.isEmpty ? "" : Locale(identifier: tr("language")).localizedString(forRegionCode: code) ?? code
}

/// "7 nov", or "7 nov 2025" in another year than `today`'s.
func dayLabel(_ epochDay: Int64, today: Int64) -> String {
    let date = RecordsKt.calendarDay(epochDay: epochDay)
    let month = tr("months_\(date.month - 1)")
    return date.year == RecordsKt.calendarDay(epochDay: today).year
        ? tr("record_date", Int(date.day), month)
        : tr("record_date_year", Int(date.day), month, Int(date.year))
}
