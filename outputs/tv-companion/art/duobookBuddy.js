/**
 * The practice buddy as a plain SVG string, for email.
 *
 * Email clients have no SVG, no CSS animation and no webfonts, so the buddy
 * ships as a raster: this builds the SVG and the caller turns it into a PNG
 * (sharp). Plain JS with no imports on purpose — the lifecycle sender runs
 * under bare Node, where src/components/PracticeBuddy.jsx (JSX, React, `@/`
 * aliases) can't load.
 *
 * Two sources, kept apart:
 *   - The body, the moods and the face details are ported by hand from
 *     PracticeBuddy.jsx: same 120×120 box, same eye/mouth paths. Keep them in
 *     sync when the component's face changes.
 *   - The colours and the wardrobe come from emails/assets/buddy-wardrobe.json,
 *     which scripts/generate-email-buddies.mjs renders out of the real
 *     components (buddyPalettes.js, PracticeBuddyOutfit.jsx), so a learner's
 *     hat in their inbox is the hat on their profile.
 *
 * Each lifecycle type has a mood (EMAIL_TYPE_BUDDIES); the learner supplies
 * the colour and the outfit. Layer order is the component's: body, hat,
 * neckwear, blush, eyes, eyewear, mouth, face details.
 */

const INK = "#7c2d12";
const PUPIL = "#1e293b";

// Big open eyes with two catchlights — the app's default idle face.
const openEyes = (pupilLeft, pupilRight, radius = 7) => `
		<ellipse cx="46" cy="55" rx="13" ry="14" fill="#ffffff"/>
		<ellipse cx="78" cy="55" rx="13" ry="14" fill="#ffffff"/>
		<circle cx="${pupilLeft.x}" cy="${pupilLeft.y}" r="${radius}" fill="${PUPIL}"/>
		<circle cx="${pupilRight.x}" cy="${pupilRight.y}" r="${radius}" fill="${PUPIL}"/>
		<circle cx="${pupilLeft.x - 2.5}" cy="${pupilLeft.y - 3}" r="2.5" fill="#ffffff"/>
		<circle cx="${pupilRight.x - 2.5}" cy="${pupilRight.y - 3}" r="2.5" fill="#ffffff"/>`;

// Sleep marks, drawn as strokes rather than text — no webfonts in email, and
// librsvg would pick whatever the host machine happens to have.
const sleepMarks = `
		<g fill="none" stroke="#a78bfa" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
			<path d="M104 21 H117 L104 37 H117"/>
			<path d="M100 45 H108 L100 56 H108" stroke-width="2.4" opacity="0.75"/>
		</g>`;

// Four-point stars flanking the head — the anticipation the eager face carries.
const star = (cx, cy, r) =>
	`M ${cx} ${cy - r} L ${cx + r * 0.3} ${cy - r * 0.3} L ${cx + r} ${cy} L ${cx + r * 0.3} ${cy + r * 0.3} L ${cx} ${cy + r} L ${cx - r * 0.3} ${cy + r * 0.3} L ${cx - r} ${cy} L ${cx - r * 0.3} ${cy - r * 0.3} Z`;

const sparkles = `
		<g fill="#fbbf24">
			<path d="${star(15, 34, 6)}"/>
			<path d="${star(107, 26, 7)}" opacity="0.95"/>
			<path d="${star(110, 45, 3.6)}" opacity="0.8"/>
		</g>`;

const MOODS = {
	happy: {
		fullBlush: true,
		eyes: `
		<g stroke="${INK}" stroke-width="5.5" stroke-linecap="round" fill="none">
			<path d="M36 56 Q46 44 56 56"/>
			<path d="M68 56 Q78 44 88 56"/>
		</g>`,
		mouth: `
		<path d="M49 73 Q62 90 75 73 Q62 80 49 73 Z" fill="${INK}"/>
		<ellipse cx="62" cy="81.5" rx="4.5" ry="2.6" fill="#fb7185"/>`,
	},
	// Eyes level and a little to the right, as if reading a card — the practice
	// nudge. Its glasses are an eyewear default (see EMAIL_TYPE_BUDDIES), so a
	// learner's own eyewear replaces them, as `wearsGlasses` does in the app.
	studious: {
		eyes: openEyes({ x: 47.5, y: 56.5 }, { x: 79.5, y: 56.5 }),
		mouth: `<path d="M55 78 Q62 84 69 78" stroke="${INK}" stroke-width="4.5" stroke-linecap="round" fill="none"/>`,
	},
	// Can't-wait-to-read face: eyes bigger and rounder than the idle ones, and
	// biting its lower lip. This mood is email-only for now — nothing in the app
	// asks for it yet, so it does not exist in PracticeBuddy.jsx.
	eager: {
		fullBlush: true,
		// Idle-sized eyes with brighter catchlights and a soft lower lid. Bigger
		// whites plus bigger pupils tipped straight into uncanny, and paired with
		// visible teeth it read as scared rather than keen.
		eyes: `
		<ellipse cx="46" cy="55" rx="13" ry="14" fill="#ffffff"/>
		<ellipse cx="78" cy="55" rx="13" ry="14" fill="#ffffff"/>
		<circle cx="47.5" cy="55.5" r="7" fill="${PUPIL}"/>
		<circle cx="79.5" cy="55.5" r="7" fill="${PUPIL}"/>
		<circle cx="45" cy="52.5" r="3" fill="#ffffff"/>
		<circle cx="77" cy="52.5" r="3" fill="#ffffff"/>
		<circle cx="50.5" cy="58.5" r="1.4" fill="#ffffff"/>
		<circle cx="82.5" cy="58.5" r="1.4" fill="#ffffff"/>`,
		// Order is what sells the bite: a narrow dark gap, then a lower lip drawn
		// wider and lower than that gap so it is unmistakably the lip and not a
		// tongue, then the white teeth row on top pressing into it. Nudged right
		// of centre so the bite looks lopsided rather than gritted.
		mouth: `
		<path d="M53 76.5 Q63 87 73 76.5 Q63 81 53 76.5 Z" fill="${INK}"/>
		<ellipse cx="65.5" cy="83.5" rx="7.2" ry="3.4" fill="#fb7185"/>
		<rect x="60" y="76.8" width="8" height="3.8" rx="1.7" fill="#ffffff"/>`,
		extras: sparkles,
	},
	sad: {
		// Cover-colored rects read as drooped upper lids, so they need the
		// palette; the tear is the same sky blue the app uses. The lids are
		// clipped to the eye whites here — unclipped (as in the component,
		// where motion hides it) the left one nicks the darker spine.
		eyes: (palette) => `
		<ellipse cx="46" cy="55" rx="13" ry="14" fill="#ffffff"/>
		<ellipse cx="78" cy="55" rx="13" ry="14" fill="#ffffff"/>
		<g clip-path="url(#eyeWhites)">
			<rect x="33" y="41" width="26" height="10" fill="${palette.cover}"/>
			<rect x="65" y="41" width="26" height="10" fill="${palette.cover}"/>
		</g>
		<circle cx="47" cy="60" r="6" fill="${PUPIL}"/>
		<circle cx="79" cy="60" r="6" fill="${PUPIL}"/>
		<circle cx="45" cy="58" r="1.8" fill="#ffffff"/>
		<circle cx="77" cy="58" r="1.8" fill="#ffffff"/>
		<path d="M85 63 C 88 67.5, 88.5 69.5, 85 71.5 C 81.5 69.5, 82 67.5, 85 63 Z" fill="#38bdf8"/>`,
		mouth: `<path d="M55 82 Q62 76 69 82" stroke="${INK}" stroke-width="4.5" stroke-linecap="round" fill="none"/>`,
	},
	// Serene closed eyes — the app's "everything's done" face, for the recap.
	proud: {
		fullBlush: true,
		eyes: `
		<g stroke="${INK}" stroke-width="5" stroke-linecap="round" fill="none">
			<path d="M37 55 Q46 63 55 55"/>
			<path d="M69 55 Q78 63 87 55"/>
		</g>`,
		mouth: `<path d="M56 79 Q62 85 68 79" stroke="${INK}" stroke-width="4.5" stroke-linecap="round" fill="none"/>`,
	},
	// Eyes down-and-in, lost in its own pages — the first-read nudge.
	reading: {
		eyes: openEyes({ x: 49.5, y: 61.5 }, { x: 76.5, y: 61.5 }, 6.5),
		mouth: `<path d="M55 78 Q62 84 69 78" stroke="${INK}" stroke-width="4.5" stroke-linecap="round" fill="none"/>`,
	},
	sleeping: {
		eyes: `
		<g stroke="${INK}" stroke-width="5" stroke-linecap="round" fill="none">
			<path d="M38 57 Q46 61 54 57"/>
			<path d="M70 57 Q78 61 86 57"/>
		</g>`,
		mouth: `<circle cx="62" cy="81" r="3.2" fill="${INK}"/>`,
		extras: sleepMarks,
	},
};

// Lifecycle email type → the face it wears, plus the stand-in buddy for a
// reader we can't look up (test sends, previews, a failed preference read):
// the colour and outfit the type's default still has always shown.
export const EMAIL_TYPE_BUDDIES = {
	welcome: { mood: "happy", fallbackVariant: "tangerine" },
	practice: { mood: "studious", fallbackVariant: "sky", defaultEyewear: "specs" },
	streak: { mood: "eager", fallbackVariant: "tangerine" },
	winback: { mood: "sleeping", fallbackVariant: "plum" },
	recap: { mood: "proud", fallbackVariant: "fern" },
	firstread: { mood: "reading", fallbackVariant: "berry" },
	// secondread shares `eager` with streak: both ask for one more read today,
	// and the two cohorts are mutually exclusive (a 1-day reader has no streak),
	// so nobody ever sees the same face twice.
	secondread: { mood: "eager", fallbackVariant: "tangerine" },
};

// Face-slot details from PracticeBuddy.jsx. They sit on top of every mood;
// the split teeth replace the mouth instead (handled in renderBuddySvg).
const FACE_DETAILS = {
	freckles: `
		<g fill="#9a3412" opacity="0.72">
			<circle cx="36" cy="69" r="1.15"/>
			<circle cx="40.5" cy="71" r="1"/>
			<circle cx="44" cy="68.5" r="0.9"/>
			<circle cx="80" cy="68.5" r="0.9"/>
			<circle cx="83.5" cy="71" r="1"/>
			<circle cx="88" cy="69" r="1.15"/>
		</g>`,
	beautymark: `
		<g>
			<circle cx="87" cy="73" r="2" fill="#713f12"/>
			<circle cx="86.4" cy="72.4" r="0.55" fill="#ffffff" opacity="0.55"/>
		</g>`,
	moustache: `<path d="M62 76 C57 70 49 72 49 77 C52 76 54 78 56 79 C59 80 61 78 62 76 C63 78 65 80 68 79 C70 78 72 76 75 77 C75 72 67 70 62 76 Z" fill="#713f12" stroke="#451a03" stroke-width="0.8" stroke-linejoin="round"/>`,
	starcheek: `<path d="M88 65.5 L89.7 69 L93.5 69.5 L90.7 72.2 L91.4 76 L88 74.2 L84.6 76 L85.3 72.2 L82.5 69.5 L86.3 69 Z" fill="#fde047" stroke="#f59e0b" stroke-width="1" stroke-linejoin="round"/>`,
};

// Gap-toothed grin — replaces whatever mouth the mood has.
const SPLIT_TEETH_MOUTH = `
		<g stroke="${INK}" stroke-linejoin="round">
			<path d="M50.5 75.5 Q62 88 73.5 75.5 Z" fill="${INK}" stroke-width="1.5"/>
			<ellipse cx="62" cy="80.4" rx="4.6" ry="2.2" fill="#fb7185" stroke="none"/>
			<rect x="56.7" y="75.6" width="4.5" height="4.8" rx="1.9" fill="#ffffff" stroke-width="1"/>
			<rect x="62.8" y="75.6" width="4.5" height="4.8" rx="1.9" fill="#ffffff" stroke-width="1"/>
		</g>`;

/**
 * @param {object} options
 * @param {string} options.mood       a key of MOODS
 * @param {object} options.palette    a BUDDY_PALETTES entry (cover, spine, …)
 * @param {object} [options.outfit]   { hat, eyewear, neck, face } item ids
 * @param {object} options.wardrobe   buddy-wardrobe.json's `items`
 * @param {number} options.size       output pixel size
 */
export const renderBuddySvg = ({ mood: moodName, palette, outfit = {}, wardrobe, size }) => {
	const mood = MOODS[moodName];
	if (!mood) throw new Error(`Unknown buddy mood "${moodName}"`);
	const itemSvg = (id) => (id && wardrobe[id]?.svg) || "";
	const hidesSprout = Boolean(outfit.hat && wardrobe[outfit.hat]?.hidesSprout);
	const blushRadius = mood.fullBlush ? 6.5 : 5.5;
	const blushOpacity = mood.fullBlush ? 1 : 0.8;
	const eyes = typeof mood.eyes === "function" ? mood.eyes(palette) : mood.eyes;
	const mouth = outfit.face === "splitteeth" ? SPLIT_TEETH_MOUTH : mood.mouth;

	return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 120 120">
	<defs>
		<clipPath id="cover">
			<rect x="22" y="16" width="76" height="92" rx="20"/>
		</clipPath>
		<clipPath id="eyeWhites">
			<ellipse cx="46" cy="55" rx="13" ry="14"/>
			<ellipse cx="78" cy="55" rx="13" ry="14"/>
		</clipPath>
	</defs>
	<!-- ground shadow: a plain ellipse instead of a blur filter so every
	     client-side rasterizer agrees on the result -->
	<ellipse cx="60" cy="113" rx="31" ry="4.5" fill="${INK}" opacity="0.1"/>
	<ellipse cx="46" cy="110" rx="9" ry="5" fill="${PUPIL}"/>
	<ellipse cx="74" cy="110" rx="9" ry="5" fill="${PUPIL}"/>
	${
		hidesSprout
			? ""
			: `<line x1="60" y1="17" x2="60" y2="9" stroke="${palette.stem}" stroke-width="2.5" stroke-linecap="round"/>
	<ellipse cx="54.5" cy="8" rx="5.5" ry="3" fill="${palette.sprout}" transform="rotate(-28 54.5 8)"/>
	<ellipse cx="65.5" cy="8" rx="5.5" ry="3" fill="${palette.sprout}" transform="rotate(28 65.5 8)"/>`
	}
	<rect x="22" y="16" width="76" height="92" rx="20" fill="${palette.cover}"/>
	<g clip-path="url(#cover)">
		<rect x="22" y="16" width="14" height="92" fill="${palette.spine}"/>
		<rect x="30" y="94" width="62" height="14" rx="6" fill="${palette.pages}"/>
		<line x1="36" y1="98" x2="86" y2="98" stroke="${palette.pageLines}" stroke-width="1.6"/>
		<line x1="36" y1="102" x2="86" y2="102" stroke="${palette.pageLines}" stroke-width="1.6"/>
	</g>
	${palette.edge ? `<rect x="22" y="16" width="76" height="92" rx="20" fill="none" stroke="${palette.edge}" stroke-width="2"/>` : ""}
	${itemSvg(outfit.hat)}
	${itemSvg(outfit.neck)}
	<circle cx="36" cy="71" r="${blushRadius}" fill="${palette.blush}" opacity="${blushOpacity}"/>
	<circle cx="88" cy="71" r="${blushRadius}" fill="${palette.blush}" opacity="${blushOpacity}"/>
	${eyes}
	${itemSvg(outfit.eyewear)}
	${mouth}
	${FACE_DETAILS[outfit.face] || ""}
	${mood.extras || ""}
</svg>`;
};
