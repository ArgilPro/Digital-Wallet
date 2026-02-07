module.exports = {
  env: {
    es6: true,
    node: true,
  },
  parserOptions: {
    'ecmaVersion': 2018,
  },
  extends: [
    'eslint:recommended',
  ],
  rules: {
    'no-restricted-globals': ['error', 'name', 'length'],
    'prefer-arrow-callback': 'error',
    'quotes': ['error', 'single', {'allowTemplateLiterals': true}],
    'max-len': ['warn', {'code': 120, 'ignoreUrls': true}],
    'comma-dangle': 'off',
    'require-jsdoc': 'off',
    'object-curly-spacing': ['error', 'never'],
    'indent': ['error', 2],
    'no-unused-vars': 'warn',
    'no-trailing-spaces': 'error',
  },
  overrides: [
    {
      files: ['**/*.spec.*'],
      env: {
        mocha: true,
      },
      rules: {},
    },
  ],
  globals: {},
};