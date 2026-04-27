import { definePreparserSetup } from '@slidev/types'

export default definePreparserSetup(() => {
  return [
    {
      name: 'invisible-separator',
      transformRawLines(lines) {
        for (let i = 0; i < lines.length; i++) {
          if (lines[i].trim() === '<!-- slide -->') {
            lines[i] = '---'
          }
        }
      },
    },
  ]
})
