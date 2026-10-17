// The buddy's cast of cover colours. Lives apart from PracticeBuddy so the
// wardrobe can offer them without importing the component that renders them —
// PracticeBuddy imports the wardrobe, and the other direction would close the
// loop.
//
// Every palette keeps the same face: the buddy is one character in many
// outfits, not six different mascots.
//
// `edge` is optional and only pale covers carry it: a hairline around the
// cover so the buddy still has a silhouette on the white and near-white
// surfaces it stands on (leaderboard rows, friend lists, the profile hero).
// Saturated covers leave it off — an outline they don't need reads as a
// sticker.

export const BUDDY_PALETTES = {
	tangerine: {
		cover: "#f97316",
		spine: "#ea580c",
		pages: "#ffedd5",
		pageLines: "#fdba74",
		blush: "#fdba74",
		sprout: "#34a853",
		stem: "#2d7d46",
	},
	fern: {
		cover: "#34a853",
		spine: "#2d7d46",
		pages: "#ecfdf5",
		pageLines: "#86efac",
		blush: "#fdba74",
		sprout: "#f97316",
		stem: "#c2410c",
	},
	sky: {
		cover: "#38bdf8",
		spine: "#0284c7",
		pages: "#e0f2fe",
		pageLines: "#7dd3fc",
		blush: "#fda4af",
		sprout: "#34a853",
		stem: "#2d7d46",
	},
	plum: {
		cover: "#a78bfa",
		spine: "#7c3aed",
		pages: "#f3e8ff",
		pageLines: "#c4b5fd",
		blush: "#f9a8d4",
		sprout: "#34a853",
		stem: "#2d7d46",
	},
	berry: {
		cover: "#fb7185",
		spine: "#e11d48",
		pages: "#fff1f2",
		pageLines: "#fda4af",
		blush: "#fecdd3",
		sprout: "#34a853",
		stem: "#2d7d46",
	},
	sunshine: {
		cover: "#fbbf24",
		spine: "#f59e0b",
		pages: "#fffbeb",
		pageLines: "#fcd34d",
		blush: "#fb923c",
		sprout: "#34a853",
		stem: "#2d7d46",
	},
	midnight: {
		cover: "#334155",
		spine: "#0f172a",
		pages: "#f8fafc",
		pageLines: "#cbd5e1",
		blush: "#fb7185",
		sprout: "#fbbf24",
		stem: "#b45309",
	},
	lagoon: {
		cover: "#14b8a6",
		spine: "#0f766e",
		pages: "#f0fdfa",
		pageLines: "#5eead4",
		blush: "#fda4af",
		sprout: "#fbbf24",
		stem: "#b45309",
	},
	rose: {
		cover: "#e879a9",
		spine: "#be185d",
		pages: "#fdf2f8",
		pageLines: "#f9a8d4",
		blush: "#fed7aa",
		sprout: "#34a853",
		stem: "#2d7d46",
	},
	ivory: {
		cover: "#f5e7c6",
		spine: "#c9a96e",
		pages: "#fffdf7",
		pageLines: "#dcc89a",
		blush: "#fb7185",
		sprout: "#34a853",
		stem: "#2d7d46",
		// Cream on a white card is a buddy-shaped hole: at avatar size the
		// cover dissolves into the row and only the face floats. The same
		// hairline the wardrobe's pale items carry gives it an edge back.
		edge: "#c9a96e",
	},
	obsidian: {
		cover: "#27272a",
		spine: "#09090b",
		pages: "#f4f4f5",
		pageLines: "#a1a1aa",
		blush: "#fb7185",
		sprout: "#a78bfa",
		stem: "#7c3aed",
	},
	lavender: {
		cover: "#c4b5fd",
		spine: "#8b5cf6",
		pages: "#faf5ff",
		pageLines: "#ddd6fe",
		blush: "#f9a8d4",
		sprout: "#34a853",
		stem: "#2d7d46",
	},
};

export const BUDDY_BASE_VARIANTS = [
	"tangerine",
	"fern",
	"sky",
	"plum",
	"berry",
	"sunshine",
];
export const BUDDY_PREMIUM_VARIANTS = [
	"midnight",
	"lagoon",
	"rose",
	"ivory",
	"obsidian",
	"lavender",
];
export const BUDDY_VARIANTS = [...BUDDY_BASE_VARIANTS, ...BUDDY_PREMIUM_VARIANTS];
